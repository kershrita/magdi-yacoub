package org.myf.ahc.core.data.repository

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.storage.ktx.storageMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.myf.ahc.core.model.storage.UploadDocumentModel
import javax.inject.Inject

class FirebaseUploadDocumentRepository @Inject constructor(
    ioDispatcher: CoroutineDispatcher
) : StorageRepository {

    private val coroutine: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val storageRef = Firebase.storage.reference
    private val user = Firebase.auth.currentUser
    private var uploadJob: Job? = null
    private var progressJob: Job? = null
    private var modifyJob: Job? = null

    override fun uploadDocument(
        path: String,
        data: ByteArray,
        name: String
    ): Flow<UploadDocumentModel> = callbackFlow {
        if (user == null){ send(UploadDocumentModel()) }
        val ref = storageRef.child("$path/${user?.uid}/$name")
        val task = ref.putBytes(data)
        uploadJob?.cancel()
        progressJob?.cancel()
        task.addOnCompleteListener { snapshot ->
            if (snapshot.isSuccessful){
                uploadJob = coroutine.launch {
                    trySendBlocking( UploadDocumentModel(
                        isUploaded = true,
                        progress = 0
                    )).onFailure {
                        close(it)
                        Log.e("upload_document",it?.message.toString())
                    }
                    channel.close()
                }
            }
        }.addOnProgressListener { result ->
            var p:Double = (100.0 * result.bytesTransferred) / result.totalByteCount
            progressJob = coroutine.launch {
                if (p.toInt() == 100){ p = 0.0 }
                trySendBlocking(UploadDocumentModel(
                    progress = p.toInt()
                )).onFailure {
                    close(it)
                    Log.e("upload_document",it?.message.toString())
                }
            }
        }.addOnFailureListener {
            uploadJob = coroutine.launch {
                trySendBlocking(UploadDocumentModel()).onFailure { t ->
                    close(t)
                    Log.e("upload_document",t?.message.toString())
                }
            }
            it.printStackTrace()
        }
        awaitClose { task.removeOnProgressListener { task.removeOnCompleteListener {  } } }
    }

    override fun addDocumentNote(
        path: String,
        note: String
    ): Flow<Boolean> = callbackFlow {
        val ref = storageRef.child(path)
        val meta = storageMetadata {
            setCustomMetadata("note",note)
        }
        val task = ref.updateMetadata(meta)
        modifyJob?.cancel()
        task.addOnSuccessListener {
            modifyJob = coroutine.launch {
                trySendBlocking(true)
                    .onFailure { t ->
                        close(t)
                    }
                channel.close()
            }
        }.addOnFailureListener {
            modifyJob = coroutine.launch {
                trySendBlocking(false)
                    .onFailure { t ->
                        close(t)
                    }
            }
            Log.e("update_document_note",it.message.toString())
        }
        awaitClose {  }
    }

    override fun deleteDocument(
        path: String
    ): Flow<Boolean> = callbackFlow {
        val ref = storageRef.child(path)
        val task = ref.delete()
        modifyJob?.cancel()
        task.addOnCompleteListener {
            modifyJob = coroutine.launch {
                trySendBlocking(true)
                    .onFailure { t ->
                        close(t)
                    }
                channel.close()
            }
        }.addOnFailureListener {
            modifyJob = coroutine.launch {
                trySendBlocking(false)
                    .onFailure { t ->
                        close(t)
                    }
            }
            Log.e("delete_document",it.message.toString())
        }
        awaitClose {  }
    }

    override fun cancelJob() = coroutine.coroutineContext.cancelChildren()

}