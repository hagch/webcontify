<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "header">
        <#if realm.attributes['brandTenantName']?? && realm.attributes['brandTenantName'] != ''>
            ${msg("welcomeToTitle", realm.attributes['brandTenantName'])}
        <#else>
            ${msg("welcomeTitle")}
        </#if>
    <#elseif section = "form">
        <p class="wc-subtitle">${msg("welcomeSetPasswordSubtitle")}</p>
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-passwd-update-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <div class="pf-v5-c-form__group">
                        <div class="pf-v5-c-form__group-label">
                            <label class="pf-v5-c-form__label" for="password-new">
                                <span class="pf-v5-c-form__label-text">${msg("passwordNew")}</span>
                            </label>
                        </div>
                        <div class="pf-v5-c-input-group">
                            <div class="pf-v5-c-input-group__item pf-m-fill">
                                <span class="pf-v5-c-form-control">
                                    <input type="password" id="password-new" name="password-new"
                                           autofocus autocomplete="new-password"
                                           aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                                    />
                                </span>
                            </div>
                            <div class="pf-v5-c-input-group__item">
                                <button class="pf-v5-c-button pf-m-plain" type="button"
                                        aria-label="${msg('showPassword')}" aria-controls="password-new"
                                        data-password-toggle
                                        data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}"
                                        data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                        data-label-show="${msg('showPassword')}"
                                        data-label-hide="${msg('hidePassword')}">
                                    <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                                </button>
                            </div>
                        </div>
                        <#if messagesPerField.existsError('password')>
                            <div class="pf-v5-c-helper-text" aria-live="polite">
                                <div class="pf-v5-c-helper-text__item pf-m-error">
                                    <span class="pf-v5-c-helper-text__item-text">
                                        ${kcSanitize(messagesPerField.get('password'))?no_esc}
                                    </span>
                                </div>
                            </div>
                        </#if>
                    </div>

                    <div class="pf-v5-c-form__group">
                        <div class="pf-v5-c-form__group-label">
                            <label class="pf-v5-c-form__label" for="password-confirm">
                                <span class="pf-v5-c-form__label-text">${msg("passwordConfirm")}</span>
                            </label>
                        </div>
                        <div class="pf-v5-c-input-group">
                            <div class="pf-v5-c-input-group__item pf-m-fill">
                                <span class="pf-v5-c-form-control">
                                    <input type="password" id="password-confirm" name="password-confirm"
                                           autocomplete="new-password"
                                           aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                                    />
                                </span>
                            </div>
                            <div class="pf-v5-c-input-group__item">
                                <button class="pf-v5-c-button pf-m-plain" type="button"
                                        aria-label="${msg('showPassword')}" aria-controls="password-confirm"
                                        data-password-toggle
                                        data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}"
                                        data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                        data-label-show="${msg('showPassword')}"
                                        data-label-hide="${msg('hidePassword')}">
                                    <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                                </button>
                            </div>
                        </div>
                        <#if messagesPerField.existsError('password-confirm')>
                            <div class="pf-v5-c-helper-text" aria-live="polite">
                                <div class="pf-v5-c-helper-text__item pf-m-error">
                                    <span class="pf-v5-c-helper-text__item-text">
                                        ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                                    </span>
                                </div>
                            </div>
                        </#if>
                    </div>

                    <div class="pf-v5-c-form__group">
                        <div class="pf-v5-c-check">
                            <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked
                                   class="pf-v5-c-check__input" />
                            <label class="pf-v5-c-check__label" for="logout-sessions">
                                ${msg("logoutOtherSessions")}
                            </label>
                        </div>
                    </div>

                    <div class="pf-v5-c-form__group">
                        <#if isAppInitiatedAction??>
                            <input name="login" class="pf-v5-c-button pf-m-primary pf-m-block pf-m-lg" type="submit" value="${msg("doSubmit")}" />
                            <button class="pf-v5-c-button pf-m-secondary pf-m-block pf-m-lg" type="submit" name="cancel-aia" value="true" style="margin-top: 0.5rem;">${msg("doCancel")}</button>
                        <#else>
                            <input name="login" class="pf-v5-c-button pf-m-primary pf-m-block pf-m-lg" type="submit" value="${msg("doSubmit")}" />
                        </#if>
                    </div>
                </form>
            </div>
        </div>
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    </#if>
</@layout.registrationLayout>
