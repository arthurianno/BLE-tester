package com.example.bletester.items

import javax.inject.Inject

data class ReportItem @Inject constructor(
    val device:String,
    val deviceAddress:String,
    val status:String,
    val interpretation : String
) {

}