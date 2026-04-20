<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>
<#assign tenantName = msg("brandTenantName")>
<#assign primaryColor = msg("brandPrimaryColor")>
<#assign logoUrl = msg("brandLogo")>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${msg("executeActionsSubject")}</title>
</head>
<body style="margin:0;padding:0;background-color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f5;padding:40px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;">

          <!-- Header -->
          <tr>
            <td align="center" style="padding:0 0 24px 0;">
              <#if logoUrl?has_content>
              <img src="${logoUrl}" alt="${tenantName}" style="max-height:48px;max-width:200px;display:block;">
              <#else>
              <span style="font-size:22px;font-weight:700;color:#18181b;">${tenantName}</span>
              </#if>
            </td>
          </tr>

          <!-- Card -->
          <tr>
            <td style="background-color:#ffffff;border-radius:8px;border:1px solid #e4e4e7;overflow:hidden;">

              <!-- Card top accent -->
              <table width="100%" cellpadding="0" cellspacing="0">
                <tr>
                  <td style="background-color:${primaryColor};height:4px;"></td>
                </tr>
              </table>

              <!-- Card body -->
              <table width="100%" cellpadding="0" cellspacing="0">
                <tr>
                  <td style="padding:40px 40px 32px 40px;">

                    <p style="margin:0 0 16px 0;font-size:24px;font-weight:700;color:#18181b;line-height:1.3;">
                      ${msg("executeActionsSubject")}
                    </p>

                    <p style="margin:0 0 24px 0;font-size:15px;color:#52525b;line-height:1.6;">
                      Your administrator has just requested that you update your <strong>${tenantName}</strong> account by performing the following action(s): ${requiredActionsText}. Click the button below to start this process.
                    </p>

                    <!-- CTA Button -->
                    <table cellpadding="0" cellspacing="0" style="margin:0 0 24px 0;">
                      <tr>
                        <td style="border-radius:6px;background-color:${primaryColor};">
                          <a href="${link}" target="_blank"
                             style="display:inline-block;padding:12px 28px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:6px;background-color:${primaryColor};">
                            Complete Required Actions
                          </a>
                        </td>
                      </tr>
                    </table>

                    <p style="margin:0 0 8px 0;font-size:13px;color:#71717a;line-height:1.5;">
                      Or copy and paste this URL into your browser:
                    </p>
                    <p style="margin:0 0 24px 0;font-size:12px;color:#6366f1;word-break:break-all;line-height:1.5;">
                      ${link}
                    </p>

                    <p style="margin:0;font-size:13px;color:#71717a;line-height:1.5;">
                      This link will expire within ${linkExpirationFormatter(linkExpiration)}. If you are unaware that your administrator has requested this, just ignore this message and nothing will be changed.
                    </p>

                  </td>
                </tr>
              </table>

            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td align="center" style="padding:24px 0 0 0;">
              <p style="margin:0;font-size:12px;color:#a1a1aa;">
                &copy; ${tenantName}. All rights reserved.
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>
