<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <#if realm.password>
                    <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
                        <#if !usernameHidden??>
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
                                        tabindex="2"
                                        id="username"
                                        name="username"
                                        value="${(login.username!'')}"
                                        type="text"
                                        autofocus
                                        autocomplete="username"
                                        aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                                    />
                                </span>
                                <#if messagesPerField.existsError('username','password')>
                                    <div class="pf-v5-c-helper-text" aria-live="polite">
                                        <div class="pf-v5-c-helper-text__item pf-m-error">
                                            <span class="pf-v5-c-helper-text__item-text">
                                                ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                            </span>
                                        </div>
                                    </div>
                                </#if>
                            </div>
                        </#if>

                        <div class="pf-v5-c-form__group">
                            <div class="pf-v5-c-form__group-label">
                                <label class="pf-v5-c-form__label" for="password">
                                    <span class="pf-v5-c-form__label-text">${msg("password")}</span>
                                </label>
                            </div>
                            <div class="pf-v5-c-input-group">
                                <div class="pf-v5-c-input-group__item pf-m-fill">
                                    <span class="pf-v5-c-form-control">
                                        <input
                                            tabindex="3"
                                            id="password"
                                            name="password"
                                            type="password"
                                            autocomplete="current-password"
                                            aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                                        />
                                    </span>
                                </div>
                                <div class="pf-v5-c-input-group__item">
                                    <button
                                        class="pf-v5-c-button pf-m-plain"
                                        type="button"
                                        aria-label="${msg('showPassword')}"
                                        aria-controls="password"
                                        data-password-toggle
                                        data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}"
                                        data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                        data-label-show="${msg('showPassword')}"
                                        data-label-hide="${msg('hidePassword')}"
                                    >
                                        <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                                    </button>
                                </div>
                            </div>
                            <#if messagesPerField.existsError('username','password')>
                                <div class="pf-v5-c-helper-text" aria-live="polite">
                                    <div class="pf-v5-c-helper-text__item pf-m-error">
                                        <span class="pf-v5-c-helper-text__item-text">
                                            ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                        </span>
                                    </div>
                                </div>
                            </#if>
                        </div>

                        <div class="pf-v5-c-form__group">
                            <div id="kc-form-options" class="wc-form-options">
                                <#if realm.rememberMe && !usernameHidden??>
                                    <div class="pf-v5-c-check">
                                        <input
                                            tabindex="5"
                                            id="rememberMe"
                                            name="rememberMe"
                                            type="checkbox"
                                            class="pf-v5-c-check__input"
                                            <#if login.rememberMe??>checked</#if>
                                        />
                                        <label class="pf-v5-c-check__label" for="rememberMe">${msg("rememberMe")}</label>
                                    </div>
                                </#if>
                                <#if realm.resetPasswordAllowed>
                                    <div class="wc-forgot-password">
                                        <a tabindex="6" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                                    </div>
                                </#if>
                            </div>
                        </div>

                        <div class="pf-v5-c-form__group">
                            <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                            <input
                                tabindex="7"
                                class="pf-v5-c-button pf-m-primary pf-m-block pf-m-lg"
                                name="login"
                                id="kc-login"
                                type="submit"
                                value="${msg("doLogIn")}"
                            />
                        </div>
                    </form>
                </#if>
            </div>
        </div>
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a tabindex="8" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders">
        <#if realm.password && social?? && social.providers?has_content>
            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
                <hr/>
                <h2>${msg("identity-provider-login-label")}</h2>
                <ul class="${properties.kcFormSocialAccountListClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountListGridClass!}</#if>">
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" class="${properties.kcFormSocialAccountListButtonClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountGridItem!}</#if>"
                               type="button" href="${p.loginUrl}">
                                <#if p.iconClasses?has_content>
                                    <i class="${properties.kcCommonLogoIdP!} ${p.iconClasses!}" aria-hidden="true"></i>
                                    <span class="${properties.kcFormSocialAccountNameClass!} kc-social-icon-text">${p.displayName!}</span>
                                <#else>
                                    <span class="${properties.kcFormSocialAccountNameClass!}">${p.displayName!}</span>
                                </#if>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
