package helpers.suppliers.respones

data class WebContifyCollectionResponse(
    val id: Long?,
    val name: String,
    val displayName: String = name,
    val fields: List<WebContifyCollectionFieldResponse>? = listOf()
) {}
