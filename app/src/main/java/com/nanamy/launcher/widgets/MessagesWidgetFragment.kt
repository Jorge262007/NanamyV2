package com.nanamy.launcher.widgets

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextPaint
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.db.MessageEntity
import com.nanamy.launcher.db.NanamyDatabase
import com.nanamy.launcher.databinding.FragmentMessagesWidgetBinding
import com.nanamy.launcher.databinding.ItemMessageWidgetBinding
import com.nanamy.launcher.messages.AiReplyGenerator
import com.nanamy.launcher.messages.NotificationReplier
import com.nanamy.launcher.messages.ReplyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Messages Tab Widget displaying live messaging conversations from Room DB.
 * Features Slack-style two-way swipe gestures:
 * - SWIPE RIGHT: Auto-reply using AI time-buying pipeline.
 * - SWIPE LEFT: Logical dismissal from widget.
 */
class MessagesWidgetFragment : Fragment() {

    private var _binding: FragmentMessagesWidgetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessagesAdapter
    private val db by lazy { NanamyDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeMessages()
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter(requireContext(), db)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MessagesWidgetFragment.adapter

            // Disallow ViewPager2 from intercepting touches ONLY when touching an active message item card
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    val child = rv.findChildViewUnder(e.x, e.y)
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            if (child != null) {
                                rv.parent?.requestDisallowInterceptTouchEvent(true)
                            } else {
                                rv.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }
            })
        }
        setupSwipeGestures()
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.messageDao().getLatestConversationsFlow().collectLatest { conversations ->
                    adapter.submitList(conversations)
                }
            }
        }
    }

    private fun setupSwipeGestures() {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
        }
        val bgRect = RectF()

        val greenColor = Color.parseColor("#4CAF50")
        val darkGreenColor = Color.parseColor("#1B5E20")
        val redColor = Color.parseColor("#EF5350")
        val darkRedColor = Color.parseColor("#B71C1C")

        val sendDrawable: Drawable? = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_send)?.mutate()?.apply {
            setTint(darkGreenColor)
        }
        val closeDrawable: Drawable? = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_close_clear_cancel)?.mutate()?.apply {
            setTint(darkRedColor)
        }

        var hapticTriggeredForViewHolder: RecyclerView.ViewHolder? = null

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return 0
                if (adapter.isRowExpanded(position)) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = adapter.getItemAt(position) ?: return

                hapticTriggeredForViewHolder = null

                if (direction == ItemTouchHelper.RIGHT) {
                    // SWIPE RIGHT = RESPONSE
                    adapter.notifyItemChanged(position)
                    (viewHolder as? MessagesAdapter.ViewHolder)?.let { holder ->
                        adapter.triggerAutomatedAiReply(holder, item)
                    }
                } else if (direction == ItemTouchHelper.LEFT) {
                    // SWIPE LEFT = DISMISS (Logical Dismissal in Room DB)
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            db.messageDao().markConversationAsDismissed(item.convKey)
                        } catch (e: Exception) {
                            Log.e("MessagesWidget", "Failed to mark convKey as dismissed", e)
                        }
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemWidth = itemView.width.toFloat()
                if (itemWidth <= 0f) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                // Prevent ViewPager2 from intercepting horizontal swipe drag
                if (isCurrentlyActive && abs(dX) > 10f) {
                    recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                }

                val thresholdFraction = 0.40f
                val thresholdPx = itemWidth * thresholdFraction
                val absDX = abs(dX)

                // Trigger single haptic feedback when crossing 40% threshold
                if (isCurrentlyActive && absDX >= thresholdPx) {
                    if (hapticTriggeredForViewHolder != viewHolder) {
                        hapticTriggeredForViewHolder = viewHolder
                        vibrateHaptic(itemView.context)
                    }
                } else if (isCurrentlyActive && absDX < thresholdPx && hapticTriggeredForViewHolder == viewHolder) {
                    hapticTriggeredForViewHolder = null
                }

                val progress = (absDX / thresholdPx).coerceIn(0f, 1f)
                val alphaInt = (progress * 255).toInt()

                if (dX > 0) {
                    // SWIPE RIGHT: Green background + "Response"
                    bgPaint.color = greenColor
                    bgRect.set(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                    c.drawRoundRect(bgRect, 32f, 32f, bgPaint)

                    textPaint.color = darkGreenColor
                    textPaint.alpha = alphaInt

                    val text = "Response"
                    val margin = 32f
                    val iconSize = 48
                    val iconLeft = (itemView.left + margin).toInt()
                    val iconTop = (itemView.top + (itemView.height - iconSize) / 2)

                    sendDrawable?.let {
                        it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                        it.alpha = alphaInt
                        it.draw(c)
                    }

                    val textX = iconLeft + iconSize + 16f
                    val textY = itemView.top + (itemView.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    c.drawText(text, textX, textY, textPaint)

                } else if (dX < 0) {
                    // SWIPE LEFT: Red background + "Dismiss"
                    bgPaint.color = redColor
                    bgRect.set(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    c.drawRoundRect(bgRect, 32f, 32f, bgPaint)

                    textPaint.color = darkRedColor
                    textPaint.alpha = alphaInt

                    val text = "Dismiss"
                    val margin = 32f
                    val iconSize = 48
                    val textWidth = textPaint.measureText(text)
                    val iconRight = (itemView.right - margin).toInt()
                    val iconLeft = iconRight - iconSize
                    val iconTop = (itemView.top + (itemView.height - iconSize) / 2)

                    closeDrawable?.let {
                        it.setBounds(iconLeft, iconTop, iconRight, iconTop + iconSize)
                        it.alpha = alphaInt
                        it.draw(c)
                    }

                    val textX = iconLeft - 16f - textWidth
                    val textY = itemView.top + (itemView.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    c.drawText(text, textX, textY, textPaint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvMessages)
    }

    private fun vibrateHaptic(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Messages Adapter ---

    private class MessagesAdapter(
        private val context: Context,
        private val db: NanamyDatabase
    ) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

        private var items = listOf<MessageEntity>()
        private var expandedPosition = -1
        private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val replier = NotificationReplier(context, db)

        fun submitList(newList: List<MessageEntity>) {
            items = newList
            notifyDataSetChanged()
        }

        fun getItemAt(position: Int): MessageEntity? {
            return if (position in items.indices) items[position] else null
        }

        fun isRowExpanded(position: Int): Boolean {
            return position == expandedPosition
        }

        class ViewHolder(val binding: ItemMessageWidgetBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMessageWidgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isExpanded = position == expandedPosition

            holder.binding.tvSenderName.text = item.senderName
            holder.binding.tvMessagePreview.text = item.messageText
            holder.binding.tvTimestamp.text = dateFormat.format(Date(item.timestamp))

            // Load app icon
            try {
                val appIcon = context.packageManager.getApplicationIcon(item.packageName)
                holder.binding.ivAppIcon.setImageDrawable(appIcon)
            } catch (_: Exception) {
                holder.binding.ivAppIcon.setImageResource(com.nanamy.launcher.R.drawable.ic_nanamy_os)
            }

            // Load Avatar photo or placeholder
            val avatarPath = item.avatarPath
            if (!avatarPath.isNullOrEmpty() && File(avatarPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarPath)
                if (bitmap != null) {
                    holder.binding.ivAvatar.setImageBitmap(bitmap)
                    holder.binding.ivAvatar.visibility = View.VISIBLE
                    holder.binding.tvAvatarInitial.visibility = View.GONE
                } else {
                    showAvatarInitial(holder, item.senderName)
                }
            } else {
                showAvatarInitial(holder, item.senderName)
            }

            // Accordion expansion state for conversation detail
            holder.binding.layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE

            holder.binding.headerLayout.setOnClickListener {
                val prevExpanded = expandedPosition
                expandedPosition = if (isExpanded) -1 else position
                if (prevExpanded != -1) notifyItemChanged(prevExpanded)
                if (expandedPosition != -1) notifyItemChanged(expandedPosition)
            }

            if (isExpanded) {
                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                    val history = db.messageDao().getMessagesForConversation(item.convKey)
                    val historyText = history.joinToString("\n") { msg ->
                        val sender = if (msg.isOutgoing) "Me" else msg.senderName
                        val time = dateFormat.format(Date(msg.timestamp))
                        "[$time] $sender: ${msg.messageText}"
                    }
                    holder.binding.tvFullHistory.text = historyText
                }
            }
        }

        fun triggerAutomatedAiReply(holder: ViewHolder, item: MessageEntity) {
            val owner = context as? androidx.lifecycle.LifecycleOwner ?: return

            com.nanamy.launcher.SafeToast.show(context, "Nanamy Replying...")

            owner.lifecycleScope.launch {
                try {
                    val history = withContext(Dispatchers.IO) {
                        db.messageDao().getMessagesForConversation(item.convKey)
                    }

                    val aiReplyText = withContext(Dispatchers.IO) {
                        AiReplyGenerator.generateTimeBuyingReply(context, history)
                    }

                    val result = replier.reply(item.convKey, aiReplyText)

                    when (result) {
                        is ReplyResult.Sent -> {
                            com.nanamy.launcher.SafeToast.show(context, "Nanamy Sent: $aiReplyText")
                        }
                        is ReplyResult.Failed -> {
                            com.nanamy.launcher.SafeToast.show(context, "Failed: ${result.reason}", Toast.LENGTH_LONG)
                            result.fallbackIntent?.let { intent ->
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.nanamy.launcher.SafeToast.show(context, "Error: ${e.message}")
                }
            }
        }

        private fun showAvatarInitial(holder: ViewHolder, senderName: String) {
            holder.binding.ivAvatar.visibility = View.GONE
            holder.binding.tvAvatarInitial.visibility = View.VISIBLE
            val initial = senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.binding.tvAvatarInitial.text = initial
            val color = generateAvatarColor(senderName)
            holder.binding.tvAvatarInitial.setBackgroundColor(color)
        }

        private fun generateAvatarColor(name: String): Int {
            val hash = name.hashCode()
            val r = (hash and 0xFF0000 shr 16) % 128 + 60
            val g = (hash and 0x00FF00 shr 8) % 128 + 60
            val b = (hash and 0x0000FF) % 128 + 60
            return Color.rgb(r, g, b)
        }

        override fun getItemCount(): Int = items.size
    }
}
