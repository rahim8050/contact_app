import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 123
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private val contactsToDelete = mutableListOf<ContactInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ContactAdapter(emptyList()) { deleteContacts(contactsToDelete) }
        recyclerView.adapter = adapter

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            loadContacts()
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadContacts()
        }
    }

    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            val contacts = processContacts()
            withContext(Dispatchers.Main) {
                adapter = ContactAdapter(contacts) { deleteContacts(contactsToDelete) }
                recyclerView.adapter = adapter
            }
        }
    }

    private fun processContacts(): List<ContactInfo> {
        val interactionMap = buildInteractionMap()
        return getContactsWithLastContactDate(interactionMap)
    }

    private fun buildInteractionMap(): Map<String, Long> {
        val callMap = getCallLogDates()
        val smsMap = getSmsDates()
        return (callMap.asSequence() + smsMap.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.maxOrNull() ?: 0 }
    }

    private fun getCallLogDates(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = normalizeNumber(cursor.getString(0))
                val date = cursor.getLong(1)
                if (number != null) {
                    map[number] = maxOf(date, map[number] ?: 0)
                }
            }
        }
        return map
    }

    private fun getSmsDates(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = normalizeNumber(cursor.getString(0))
                val date = cursor.getLong(1)
                if (number != null) {
                    map[number] = maxOf(date, map[number] ?: 0)
                }
            }
        }
        return map
    }

    private fun getContactsWithLastContactDate(interactionMap: Map<String, Long>): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
        val threeMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)
        }.timeInMillis

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.LOOKUP_KEY
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val lookupKey = cursor.getString(2)
                val numbers = getContactNumbers(id)
                val lastContact = numbers.mapNotNull { interactionMap[it] }.maxOrNull()

                if (lastContact == null || lastContact < threeMonthsAgo) {
                    contacts.add(ContactInfo(id, lookupKey, name, lastContact))
                }
            }
        }
        return contacts
    }

    private fun getContactNumbers(contactId: String): List<String> {
        val numbers = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                normalizeNumber(cursor.getString(0))?.let { numbers.add(it) }
            }
        }
        return numbers
    }

    private fun normalizeNumber(number: String?): String? {
        return number?.replace("[^0-9]".toRegex(), "")
    }

    private fun deleteContacts(contacts: List<ContactInfo>) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Delete")
            .setMessage("Delete ${contacts.size} contacts?")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    contacts.forEach { contact ->
                        try {
                            val uri = ContactsContract.Contacts.getLookupUri(
                                contact.id.toLong(),
                                contact.lookupKey
                            )
                            uri?.let { contentResolver.delete(it, null, null) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        loadContacts() // Refresh list after deletion
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class ContactInfo(
    val id: String,
    val lookupKey: String,
    val name: String,
    val lastContactDate: Long?
)