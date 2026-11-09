package com.dogyun.kairos.seller.application

data class SellerApplicationCommand(
    val companyName: String,
    val businessNumber: String,
    val representativeName: String,
    val phone: String,
    val bank: String,
    val accountNumber: String,
    val holder: String,
)
