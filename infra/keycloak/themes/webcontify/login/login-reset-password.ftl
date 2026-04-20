<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
                    <div class="pf-v5-c-form__group">
                        <div class="pf-v5-c-form__group-label">
                            <label class="pf-v5-c-form__label" for="username">
                                <span class="pf-v5-c-form__label-text">
                                    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                                </span>
                            </label>
                        </div>
                        <span class="pf-v5-c-form-control">
                            <input
                                type="text"
                                id="username"
                                name="username"
                                autofocus
                                value="${(auth.attemptedUsername!'')}"
                                aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                            />
                        </span>
                        <#if messagesPerField.existsError('username')>
                            <div class="pf-v5-c-helper-text" aria-live="polite">
                                <div class="pf-v5-c-helper-text__item pf-m-error">
                                    <span class="pf-v5-c-helper-text__item-text">
                                        ${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}
                                    </span>
                                </div>
                            </div>
                        </#if>
                    </div>

                    <div class="pf-v5-c-form__group">
                        <div class="wc-form-options">
                            <a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
                        </div>
                    </div>

                    <div class="pf-v5-c-form__group">
                        <input
                            class="pf-v5-c-button pf-m-primary pf-m-block pf-m-lg"
                            type="submit"
                            value="${msg("doSubmit")}"
                        />
                    </div>
                </form>
            </div>
        </div>
    <#elseif section = "info">
        <#if realm.duplicateEmailsAllowed>
            ${msg("emailInstructionUsername")}
        <#else>
            ${msg("emailInstruction")}
        </#if>
    </#if>
</@layout.registrationLayout>
