package com.example.taller2_lapiceselite


import android.content.pm.PackageManager
import android.database.Cursor
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2_lapiceselite.Datos.Companion.MY_PERMISSION_REQUEST_READ_CONTACTS

class Contacts : AppCompatActivity() {

    var mProjection: Array<String>? = null
    var mCursor: Cursor? = null
    var mContactsAdapter: ContactsAdapter? = null
    var mlista: ListView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        //1. Variables
        mlista = findViewById(R.id.listaContactos)

        //2. Proyección
        mProjection = arrayOf(ContactsContract.Profile._ID, ContactsContract.Profile.DISPLAY_NAME_PRIMARY)

        //3. Adaptador
        mContactsAdapter = ContactsAdapter(this, null, 0)
        mlista?.adapter = mContactsAdapter

        //4. Permisos
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, load contacts
                showContacts()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.READ_CONTACTS
            ) -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_CONTACTS),
                    MY_PERMISSION_REQUEST_READ_CONTACTS
                )
            }
            else -> {
                // You can directly ask for the permission.
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_CONTACTS),
                    MY_PERMISSION_REQUEST_READ_CONTACTS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSION_REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, load contacts
                    showContacts()
                    Toast.makeText(this, "¡Permiso concedido!", Toast.LENGTH_SHORT).show()
                } else {
                    // Permission denied, show toast or handle accordingly
                    Toast.makeText(this, "¡Funcionalidades reducidas!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun showContacts() {
        //Usa el cursor para llenar el adaptador
        mCursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, mProjection, null, null, null
        )
        mContactsAdapter?.changeCursor(mCursor)
    }
}