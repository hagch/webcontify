<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${kcSanitize(msg("${messageHeader}"))?no_esc}
        <#else>
            ${message.summary}
        </#if>
    <#elseif section = "form">
        <div id="kc-info-message">
            <#if actionUri?has_content>
                <script>window.location.href = "${actionUri?no_esc}";</script>
                <noscript>
                    <div class="wc-noscript-card">
                        <p>Click below to continue setting up your account.</p>
                        <a href="${actionUri}" class="wc-btn-primary">Continue</a>
                    </div>
                </noscript>
            <#else>
                <#if requiredActions?? && requiredActions?has_content>
                    <p class="instruction">${message.summary}: <b><#list requiredActions as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#list></b></p>
                </#if>
                <#if skipLink??>
                <#else>
                    <#if pageRedirectUri?has_content>
                        <p><a href="${pageRedirectUri}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                    <#elseif (client.baseUrl)?has_content>
                        <p><a href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                    </#if>
                </#if>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
