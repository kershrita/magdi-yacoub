package org.myf.ahc.util

object FileTypesUtil {
    const val MICROSOFT_WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val PDF = "application/pdf"
    const val PNG = "image/png"
    const val JPG = "image/jpeg"
    const val JPG_EX = ".jpg"
    const val WORD_EX = ".docx"
    const val PDF_EX = ".pdf"
    const val PNG_EX = ".png"

    fun getFileTypeExtension(type: String): String = when(type){
        MICROSOFT_WORD -> WORD_EX
        PDF -> PDF_EX
        JPG -> JPG_EX
        PNG -> PNG_EX
        else -> ""
    }
}