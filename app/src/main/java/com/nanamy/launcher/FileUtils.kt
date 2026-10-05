package com.nanamy.launcher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        // Fallback: Copy to cache to get a real path for llama.cpp
        return try {
            val returnCursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            returnCursor?.moveToFirst()
            val fileName = returnCursor?.getString(nameIndex) ?: "model.gguf"
            returnCursor?.close()

            val file = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Initializes the directory structure in the app's internal storage.
     * Creates: Desktop/ (beside Home)
     * Creates: Home/, Home/Documents/, Home/Videos/, Home/Pictures/, Home/Music/, Home/Download/
     */
    fun initHomeDirectory(context: Context) {
        // Create Desktop directory alongside Home for OS desktop items
        val desktopDir = File(context.filesDir, "Desktop")
        if (!desktopDir.exists()) {
            desktopDir.mkdirs()
        }

        // Create the main Home directory and its user folders
        val homeDir = File(context.filesDir, "Home")
        val subDirs = listOf("Documents", "Videos", "Pictures", "Music", "Download")

        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }

        // Cleanup: remove Desktop from inside Home if it was created there previously
        val oldDesktopInHome = File(homeDir, "Desktop")
        if (oldDesktopInHome.exists()) {
            oldDesktopInHome.deleteRecursively()
        }

        subDirs.forEach { subDirName ->
            val subDir = File(homeDir, subDirName)
            if (!subDir.exists()) {
                subDir.mkdirs()
            }
        }

        // Install/Update Lex Installer Tool in Home
        val installerFile = File(homeDir, "lex_installer.lex")
        val installerManifest = """
        {
          "name": "Lex Installer",
          "icon": "✦",
          "width": 550,
          "height": 650,
          "html": "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" /><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" /><title>Lex Installer</title><style>:root{--tetris-cyan:#39ff14;--bg-dark:#080808;--panel-bg:#121212;--grid-line:#222;--text-glow:0 0 5px rgba(57,255,20,.8);}html,body{height:100%;}body{margin:0;padding:20px;min-height:100vh;box-sizing:border-box;background:var(--bg-dark);color:#e1e1e1;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;}h1{font-size:18px;color:var(--tetris-cyan);text-shadow:var(--text-glow);text-transform:uppercase;letter-spacing:2px;margin-bottom:24px;text-align:center;}.panel{background:transparent;border:none;padding:0;width:100%;max-width:480px;box-shadow:none;}label{display:block;font-size:10px;margin:14px 0 6px;color:#9be89b;}input[type=text]{width:100%;box-sizing:border-box;padding:10px;background:#000;border:1px solid var(--grid-line);color:#e1e1e1;font-family:monospace;font-size:13px;border-radius:4px;}.dropzone-area{display:flex;gap:8px;margin-top:14px;}.dropzone{flex:1;border:2px dashed var(--grid-line);border-radius:6px;padding:20px;text-align:center;font-size:10px;color:#888;cursor:pointer;transition:.15s;}.dropzone.drag{border-color:var(--tetris-cyan);color:var(--tetris-cyan);}.dropzone.loaded{border-color:var(--tetris-cyan);color:var(--tetris-cyan);}.browse-btn{width:80px;background:#161b22;border:1px solid var(--grid-line);color:#9be89b;font-size:10px;cursor:pointer;border-radius:6px;}input[type=file]{display:none;}button.main-btn{margin-top:20px;width:100%;padding:14px;background:var(--tetris-cyan);color:#000;border:none;border-radius:4px;font-weight:bold;font-size:11px;cursor:pointer;text-transform:uppercase;letter-spacing:1px;}button.main-btn:disabled{background:#333;color:#777;cursor:not-allowed;}button.main-btn:hover:not(:disabled){filter:brightness(1.15);}.msg{margin-top:12px;font-size:10px;text-align:center;min-height:14px;color:#9be89b;}.msg.err{color:#ff5555;}.icon-picker-wrap{position:relative;}.icon-btn{margin-top:0;width:100%;padding:10px;background:#000;border:1px solid var(--grid-line);color:#e1e1e1;font-family:monospace;font-size:13px;border-radius:4px;display:flex;align-items:center;gap:10px;cursor:pointer;text-transform:none;letter-spacing:normal;}.icon-btn:hover{border-color:var(--tetris-cyan);filter:none;}#iconPreview{font-size:20px;line-height:1;}.icon-btn-label{font-size:10px;color:#9be89b;}.emoji-panel{display:none;position:absolute;top:calc(100% + 6px);left:0;right:0;background:#000;border:1px solid var(--tetris-cyan);border-radius:6px;padding:10px;z-index:10;max-height:220px;overflow-y:auto;box-shadow:0 0 20px rgba(57,255,20,.25);}.emoji-panel.open{display:block;}.emoji-search{width:100%;box-sizing:border-box;padding:8px;margin-bottom:8px;background:#121212;border:1px solid var(--grid-line);color:#e1e1e1;font-family:monospace;font-size:12px;border-radius:4px;}.emoji-grid{display:grid;grid-template-columns:repeat(8,1fr);gap:4px;}.emoji-grid button{all:unset;text-align:center;font-size:18px;padding:4px 0;border-radius:4px;cursor:pointer;width:100%;box-sizing:border-box;}.emoji-grid button:hover{background:rgba(57,255,20,.15);}.emoji-none{font-size:10px;color:#888;text-align:center;padding:10px 0;}.file-list{display:none;position:absolute;background:#000;border:1px solid var(--tetris-cyan);width:100%;z-index:20;max-height:200px;overflow-y:auto;border-radius:6px;padding:10px;margin-top:5px;}.file-item{padding:8px;font-size:11px;cursor:pointer;border-bottom:1px solid var(--grid-line);}.file-item:hover{background:rgba(57,255,20,0.1);}</style></head><body><h1>Lex Installer</h1><div class=\"panel\"><label for=\"name\">App Name</label><input type=\"text\" id=\"name\" placeholder=\"Enter app name\" /><label for=\"icon\">Icon</label><div class=\"icon-picker-wrap\"><button type=\"button\" id=\"iconBtn\" class=\"icon-btn\"><span id=\"iconPreview\">&#x1F3AE;</span><span class=\"icon-btn-label\">Choose emoji</span></button><input type=\"hidden\" id=\"icon\" value=\"&#x1F3AE;\" /><div class=\"emoji-panel\" id=\"emojiPanel\"></div></div><label>HTML Source</label><div class=\"dropzone-area\"><div class=\"dropzone\" id=\"dropzone\">Drop .html here</div><button class=\"browse-btn\" id=\"browseHome\">Home</button><button class=\"browse-btn\" id=\"browseDesktop\">Desktop</button></div><div id=\"fileList\" class=\"file-list\"></div><input type=\"file\" id=\"fileInput\" accept=\".html,.htm\" /><button id=\"buildBtn\" class=\"main-btn\" disabled>Install .lex to Home</button><div class=\"msg\" id=\"msg\"></div></div><script>const EMOJIS=['🎮','🕹️','👾','🎯','🎲','🧩','🃏','🎰','🎳','🚀','🛸','🌟','🔥','💡','💻','📱','🎵','🎬','🛠️','🤖','🐉','🍕','🍦','💎'];const nameInput=document.getElementById('name');const iconInput=document.getElementById('icon');const iconBtn=document.getElementById('iconBtn');const iconPreview=document.getElementById('iconPreview');const emojiPanel=document.getElementById('emojiPanel');const dropzone=document.getElementById('dropzone');const fileInput=document.getElementById('fileInput');const buildBtn=document.getElementById('buildBtn');const msg=document.getElementById('msg');const fileList=document.getElementById('fileList');let htmlContent=null;dropzone.onclick=()=>fileInput.click();document.getElementById('browseHome').onclick=()=>listServerFiles('/Home');document.getElementById('browseDesktop').onclick=()=>listServerFiles('/Desktop');async function listServerFiles(path){try{const r=await fetch('/api/files?path='+encodeURIComponent(path));const d=await r.json();const items=(d.items||[]).filter(i=>!i.isDir && i.name.match(/\\.html?$/i));fileList.innerHTML='';fileList.style.display='block';if(items.length===0){fileList.innerHTML='<div class=\"file-item\">No .html files found</div>';}items.forEach(it=>{const div=document.createElement('div');div.className='file-item';div.textContent=it.name;div.onclick=async()=>{const filePath=path+'/'+it.name;const content=await fetch('/api/raw?path='+encodeURIComponent(filePath));htmlContent=await content.text();dropzone.textContent='Loaded: '+it.name;dropzone.classList.add('loaded');if(!nameInput.value)nameInput.value=it.name.replace(/\\.html?$/i,'');fileList.style.display='none';updateButton();};fileList.appendChild(div);});}catch(e){showMsg('Error listing files',true);}}fileInput.onchange=e=>{const f=e.target.files[0];if(f)handleFile(f);};function handleFile(file){const reader=new FileReader();reader.onload=()=>{htmlContent=reader.result;dropzone.textContent='Loaded: '+file.name;dropzone.classList.add('loaded');if(!nameInput.value)nameInput.value=file.name.replace(/\\.html?$/i,'');updateButton();};reader.readAsText(file);}function updateButton(){buildBtn.disabled=!(htmlContent && nameInput.value.trim());}nameInput.oninput=updateButton;function buildPanel(){emojiPanel.innerHTML='<input type=\"text\" class=\"emoji-search\" placeholder=\"Search...\"/><div class=\"emoji-grid\"></div>';const grid=emojiPanel.querySelector('.emoji-grid');EMOJIS.forEach(e=>{const b=document.createElement('button');b.textContent=e;b.onclick=()=>{iconInput.value=e;iconPreview.textContent=e;emojiPanel.classList.remove('open');};grid.appendChild(b);});}buildPanel();iconBtn.onclick=(e)=>{e.stopPropagation();emojiPanel.classList.toggle('open');};buildBtn.onclick=async()=>{const name=nameInput.value.trim();const filename=name+'.lex';const path='/Home/'+filename;showMsg('Installing...',false);try{const r=await fetch('/api/save?path='+encodeURIComponent(path),{method:'POST',body:JSON.stringify({name,icon:iconInput.value,html:htmlContent})});showMsg(r.ok?'Installed to '+path:'Error saving',!r.ok);}catch(e){showMsg('Connection error',true);}};function showMsg(t,e){msg.textContent=t;msg.className='msg'+(e?' err':'');}document.onclick=e=>{if(!emojiPanel.contains(e.target)&&e.target!==iconBtn)emojiPanel.classList.remove('open');if(!fileList.contains(e.target))fileList.style.display='none';};<\/script></body></html>"
        }
        """.trimIndent()
        try {
            installerFile.writeText(installerManifest)
            android.util.Log.d("FileUtils", "Lex Installer updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to update lex_installer", e)
        }
    }
}
