package com.nanamy.launcher.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.notes.NoteEntry
import com.nanamy.launcher.notes.NotesFileParser
import com.nanamy.launcher.notes.NotesViewModel
import com.nanamy.launcher.databinding.FragmentNotesWidgetBinding
import com.nanamy.launcher.databinding.ItemNoteBinding
import com.nanamy.launcher.databinding.DialogEditNoteBinding
import kotlinx.coroutines.launch

class NotesWidgetFragment : Fragment() {

    private var _binding: FragmentNotesWidgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var notesParser: NotesFileParser
    private val notesViewModel: NotesViewModel by activityViewModels()
    private var isAddMode = false
    private var expandedPosition = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notesParser = NotesFileParser(requireContext())
        
        setupUI()
        setupObserver()
        loadNotes()
    }

    private fun setupUI() {
        binding.btnAddNoteMode.setOnClickListener {
            isAddMode = !isAddMode
            updateAddModeUI()
        }
    }

    private fun updateAddModeUI() {
        binding.btnAddNoteMode.setColorFilter(if (isAddMode) 0xFF00BFA5.toInt() else 0xFFFFFFFF.toInt())
        if (isAddMode) {
            Toast.makeText(context, "Edit Mode Active: Tap a note or '+' for new", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notesViewModel.notesUpdateFlow.collect {
                    loadNotes()
                }
            }
        }
    }

    private fun loadNotes() {
        val notes = notesParser.getAllNotes()
        binding.rvNotes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = NotesAdapter(notes)
        }
    }

    private fun showAddEditDialog(note: NoteEntry?) {
        val dialogBinding = DialogEditNoteBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()

        note?.let {
            dialogBinding.etNoteTitle.setText(it.title)
            dialogBinding.etNoteContent.setText(it.content)
            dialogBinding.btnDeleteNote.visibility = View.VISIBLE
        }

        dialogBinding.btnCancelNote.setOnClickListener {
            isAddMode = false
            updateAddModeUI()
            dialog.dismiss()
        }

        dialogBinding.btnDeleteNote.setOnClickListener {
            note?.let { notesParser.deleteNote(it.id) }
            notesViewModel.notifyNotesChanged()
            isAddMode = false
            updateAddModeUI()
            dialog.dismiss()
        }

        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etNoteTitle.text.toString().ifBlank { "Untitled" }
            val content = dialogBinding.etNoteContent.text.toString()
            
            val newNote = note?.copy(title = title, content = content) ?: NoteEntry(title = title, content = content)
            notesParser.saveNote(newNote)
            notesViewModel.notifyNotesChanged()
            
            isAddMode = false
            updateAddModeUI()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class NotesAdapter(private val notes: List<NoteEntry>) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

        inner class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return NoteViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notes[position]
            holder.binding.tvNoteTitle.text = note.title
            holder.binding.tvNoteContent.text = note.content

            val isExpanded = position == expandedPosition
            holder.binding.contentArea.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.binding.ivChevron.rotation = if (isExpanded) 90f else 0f

            holder.binding.headerNote.setOnClickListener {
                if (isAddMode) {
                    showAddEditDialog(note)
                } else {
                    val prevExpanded = expandedPosition
                    expandedPosition = if (isExpanded) -1 else position
                    notifyItemChanged(prevExpanded)
                    notifyItemChanged(expandedPosition)
                }
            }
        }

        override fun getItemCount(): Int = notes.size
    }
}
