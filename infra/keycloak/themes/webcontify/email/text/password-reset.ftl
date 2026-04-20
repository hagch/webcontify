<#ftl output_format="plainText">
<#assign tenantName = msg("brandTenantName")>
${tenantName}
---

${msg("passwordResetBody", link, linkExpiration, realmName, linkExpirationFormatter(linkExpiration))}

---
${tenantName}
