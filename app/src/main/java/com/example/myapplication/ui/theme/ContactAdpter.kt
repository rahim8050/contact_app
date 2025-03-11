import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ContactAdapter(
    private val contacts: List<ContactInfo>,
    private val deleteCallback: (List<ContactInfo>) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    private val selectedContacts = mutableListOf<ContactInfo>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.contactName)
        val date: TextView = view.findViewById(R.id.lastContactDate)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.name.text = contact.name
        holder.date.text = contact.lastContactDate?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Never contacted"

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedContacts.add(contact)
            } else {
                selectedContacts.remove(contact)
            }
        }
    }

    override fun getItemCount() = contacts.size

    fun deleteSelected() {
        deleteCallback(selectedContacts.toList())
    }
}