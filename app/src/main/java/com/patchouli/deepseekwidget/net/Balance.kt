package com.patchouli.deepseekwidget.net

import java.math.BigDecimal

data class Balance(
    val total: BigDecimal,
    val granted: BigDecimal,
    val topped: BigDecimal,
    val currency: String,
    val isAvailable: Boolean
)

sealed class ApiResult {
    data class Success(val balance: Balance) : ApiResult()
    data object MissingKey : ApiResult()
    data object Unauthorized : ApiResult()
    data class NetworkError(val detail: String) : ApiResult()
    data class Unexpected(val detail: String) : ApiResult()
}
