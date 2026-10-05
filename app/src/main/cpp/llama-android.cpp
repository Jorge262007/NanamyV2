#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <unistd.h>
#include <chrono>
#include <filesystem>
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "NanamyLlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_batch g_batch;
static common_sampler* g_sampler = nullptr;
static bool g_is_busy = false;
static bool g_should_stop = false;

static void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void)level;
    (void)user_data;
    LOGI("%s", text);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nanamy_launcher_localllm_LocalLlmEngine_initModelNative(JNIEnv* env, jobject thiz, jstring model_path, jstring native_lib_dir) {
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    llama_batch_free(g_batch);

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    const char* lib_dir = env->GetStringUTFChars(native_lib_dir, nullptr);

    llama_log_set(llama_log_callback, nullptr);

    LOGI("Loading backends for Nanamy...");

    // Bypass SELinux readdir blocking by attempting to load known optimized variants by name
    std::vector<std::string> backends_to_try = {
        "libggml-cpu-android_armv9.2_2.so",
        "libggml-cpu-android_armv9.2_1.so",
        "libggml-cpu-android_armv9.0_1.so",
        "libggml-cpu-android_armv8.6_1.so",
        "libggml-cpu-android_armv8.2_2.so",
        "libggml-cpu-android_armv8.2_1.so",
        "libggml-cpu-android_armv8.0_1.so",
        "libggml-cpu.so",
        "libggml-opencl.so"
    };

    for (const auto& name : backends_to_try) {
        ggml_backend_load(name.c_str());
    }

    llama_backend_init();

    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // Force CPU to ensure KleidiAI kernels are used
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;

    g_model = llama_model_load_from_file(path, mparams);
    if (!g_model) {
        LOGE("Model load failed: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        env->ReleaseStringUTFChars(native_lib_dir, lib_dir);
        return JNI_FALSE;
    }

    // Optimized thread count: Use 4 threads (common for performance cores in mobile)
    // Using too many threads (including E-cores) often slows down llama.cpp
    int n_threads = 4;
    LOGI("Using %d optimized threads for inference", n_threads);

    auto cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = 4; // Generation on P-cores
    cparams.n_threads_batch = 8; // Batch on all cores
    cparams.offload_kqv = true;

    // Explicitly set KV cache types to Q8_0 to match Q8_0 models
    // This provides better precision for small models without much speed loss
    cparams.type_k = GGML_TYPE_Q8_0;
    cparams.type_v = GGML_TYPE_Q8_0;

    g_ctx = llama_init_from_model(g_model, cparams);
    g_batch = llama_batch_init(512, 0, 1);

    // Optimized for Q8_0 models and tiny parameters (0.5B - 1.5B)
    common_params_sampling sparams;
    sparams.temp = 0.7f;
    sparams.penalty_last_n = 64;
    sparams.penalty_repeat = 1.18f;
    sparams.penalty_freq = 0.05f;
    sparams.penalty_present = 0.05f;
    sparams.top_k = 40;
    sparams.top_p = 0.95f;
    sparams.min_p = 0.10f;

    // Native suppression of reasoning/thinking blocks
    // This corresponds to both --reasoning-budget 0 and reasoning_budget_tokens = 0
    sparams.reasoning_budget_tokens = 0;

    const struct llama_vocab* vocab = llama_model_get_vocab(g_model);
    sparams.reasoning_budget_start = common_tokenize(vocab, "<think>", false, true);
    sparams.reasoning_budget_end.push_back(common_tokenize(vocab, "</think>", false, true));

    g_sampler = common_sampler_init(g_model, sparams);

    env->ReleaseStringUTFChars(model_path, path);
    env->ReleaseStringUTFChars(native_lib_dir, lib_dir);

    return (g_ctx && g_sampler) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanamy_launcher_localllm_LocalLlmEngine_unloadModelNative(JNIEnv* env, jobject thiz) {
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    llama_batch_free(g_batch);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nanamy_launcher_localllm_LocalLlmEngine_generateNative(JNIEnv* env, jobject thiz, jstring prompt) {
    if (!g_ctx || g_is_busy) return env->NewStringUTF("Error: Engine busy or not loaded");
    g_is_busy = true;
    g_should_stop = false;

    const char* prompt_text = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab* vocab = llama_model_get_vocab(g_model);

    auto tokens = common_tokenize(vocab, prompt_text, true, true);
    env->ReleaseStringUTFChars(prompt, prompt_text);

    llama_memory_clear(llama_get_memory(g_ctx), false);
    common_sampler_reset(g_sampler);

    // Decode prompt in batches
    for (int i = 0; i < tokens.size(); i += 512) {
        int n_eval = std::min((int)tokens.size() - i, 512);
        g_batch.n_tokens = 0;
        for (int j = 0; j < n_eval; j++) {
            g_batch.token[g_batch.n_tokens] = tokens[i + j];
            g_batch.pos[g_batch.n_tokens] = i + j;
            g_batch.n_seq_id[g_batch.n_tokens] = 1;
            g_batch.seq_id[g_batch.n_tokens][0] = 0;
            g_batch.logits[g_batch.n_tokens] = (i + j == tokens.size() - 1);
            g_batch.n_tokens++;
        }
        if (llama_decode(g_ctx, g_batch) != 0) {
            LOGE("Prompt decode failed");
            g_is_busy = false;
            return env->NewStringUTF("Error: Decode failed");
        }
    }

    std::string result = "";
    int n_cur = tokens.size();
    int n_ctx = llama_n_ctx(g_ctx);
    int n_gen = 0;

    auto t_start = std::chrono::high_resolution_clock::now();

    while (n_cur < n_ctx && !g_should_stop && n_gen < 256) {
        const llama_token id = common_sampler_sample(g_sampler, g_ctx, -1);
        common_sampler_accept(g_sampler, id, true);

        if (llama_vocab_is_eog(vocab, id)) break;

        std::string piece = common_token_to_piece(g_ctx, id);
        result += piece;

        // Early stopping for common dialogue markers
        const char* stop_tokens[] = {"\nUser:", "\nAssistant:", "<|im_end|>", "<|eot_id|>", "<|endoftext|>", "<|prompt|>", "<|answer|>"};
        bool should_break = false;
        for (const char* token : stop_tokens) {
            size_t pos = result.find(token);
            if (pos != std::string::npos) {
                result = result.substr(0, pos); // Trim the stop token immediately
                should_break = true;
                break;
            }
        }
        if (should_break) break;

        g_batch.n_tokens = 0;
        g_batch.token[0] = id;
        g_batch.pos[0] = n_cur;
        g_batch.n_seq_id[0] = 1;
        g_batch.seq_id[0][0] = 0;
        g_batch.logits[0] = true;
        g_batch.n_tokens = 1;

        if (llama_decode(g_ctx, g_batch) != 0) break;

        n_cur++;
        n_gen++;
    }

    auto t_end = std::chrono::high_resolution_clock::now();
    double duration = std::chrono::duration<double>(t_end - t_start).count();
    LOGI("Nanamy Local LLM: %d tokens, %.2f s, %.2f tokens/s", n_gen, duration, n_gen / (duration > 0 ? duration : 1));

    g_is_busy = false;
    return env->NewStringUTF(result.c_str());
}
