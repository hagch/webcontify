<#macro username>
<#assign label>
    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
</#assign>
<div class="${properties.kcFormGroupClass!}">
    <label for="kc-attempted-username" class="${properties.kcLabelClass!}">${label}</label>
    <div class="${properties.kcInputGroup}">
      <div class="${properties.kcInputGroupItemClass} ${properties.kcFill}">
            <span class="${properties.kcInputClass} ${properties.kcFormReadOnlyClass}">
              <input id="kc-attempted-username" value="${auth.attemptedUsername}" readonly>
            </span>
      </div>
      <div class="${properties.kcInputGroupItemClass}">
        <button id="reset-login" class="${properties.kcFormPasswordVisibilityButtonClass} kc-login-tooltip" type="button"
                aria-label="${msg('restartLoginTooltip')}" onclick="location.href='${url.loginRestartFlowUrl}'">
          <i class="fa-sync-alt fas" aria-hidden="true"></i>
          <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
        </button>
      </div>
    </div>
</div>
</#macro>

    <#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
      <!DOCTYPE html>
      <html class="${properties.kcHtmlClass!}"<#if realm.internationalizationEnabled> lang="${locale.currentLanguageTag}" dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>

      <head>
        <meta charset="utf-8">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="robots" content="noindex, nofollow">

          <#if properties.meta?has_content>
              <#list properties.meta?split(' ') as meta>
                <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
              </#list>
          </#if>
        <title>${msg("loginTitle",(realm.displayName!''))}</title>
        <#if realm.attributes['brandFavicon']?? && realm.attributes['brandFavicon'] != ''>
        <link rel="icon" href="${realm.attributes['brandFavicon']}" />
        <#else>
        <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
        </#if>
          <#if properties.stylesCommon?has_content>
              <#list properties.stylesCommon?split(' ') as style>
                <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
              </#list>
          </#if>
          <#if properties.styles?has_content>
              <#list properties.styles?split(' ') as style>
                <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
              </#list>
          </#if>
        <script type="importmap">
          {
              "imports": {
                  "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
        </script>
          <#if properties.scripts?has_content>
              <#list properties.scripts?split(' ') as script>
                <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
              </#list>
          </#if>
          <#if scripts??>
              <#list scripts as script>
                <script src="${script}" type="text/javascript"></script>
              </#list>
          </#if>
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
        <script type="module">
          import { startSessionPolling } from "${url.resourcesPath}/js/authChecker.js";

          startSessionPolling(
              "${url.ssoLoginInOtherTabsUrl?no_esc}"
          );

        </script>

          <#-- Tenant Branding: CSS Variables aus Realm-Attributes -->
        <style>
          :root {
            /* Dynamic (from Realm Attributes) */
            --wc-primary: ${(realm.attributes['brandPrimaryColor']!'#6366F1')?no_esc};
            --wc-primary-hover: color-mix(in srgb, var(--wc-primary) 85%, black);
            --wc-secondary: ${(realm.attributes['brandSecondaryColor']!'#10B981')?no_esc};
            --wc-accent: ${(realm.attributes['brandAccentColor']!'#10B981')?no_esc};
            --wc-radius: ${(realm.attributes['brandBorderRadius']!'0.5rem')?no_esc};
            --wc-font: ${(realm.attributes['brandFontFamily']!'system-ui, sans-serif')?no_esc};

            /* Static (design system tokens) */
            --wc-bg: #FAFAF9;
            --wc-card: #FFFFFF;
            --wc-card-border: #E7E5E4;
            --wc-text: #1C1917;
            --wc-text-muted: #78716C;
            --wc-input-border: #D6D3D1;
            --wc-input-bg: #FFFFFF;
            --wc-focus-ring: var(--wc-primary);
            --wc-link: var(--wc-primary);
            --wc-error: #EF4444;
          }
        </style>
          <#-- Load Google Fonts if a non-system font is configured -->
          <#if realm.attributes['brandFontFamily']?? && realm.attributes['brandFontFamily'] != 'system-ui, sans-serif'>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
              <#assign fontName = realm.attributes['brandFontFamily']?replace("'", "")?split(",")?first?trim>
              <#if fontName != "system-ui">
        <link href="https://fonts.googleapis.com/css2?family=${fontName?url('UTF-8')}:wght@400;500;600;700&display=swap" rel="stylesheet">
              </#if>
          </#if>
      </head>

      <body id="keycloak-bg" class="${properties.kcBodyClass!}">

      <div class="${properties.kcLogin!}">
        <div class="${properties.kcLoginContainer!}">
          <header id="kc-header" class="pf-v5-c-login__header">
            <div id="kc-header-wrapper" class="pf-v5-c-brand wc-brand-header">
                <#if realm.attributes['brandLogo']?? && realm.attributes['brandLogo'] != ''>
                  <img
                      src="${realm.attributes['brandLogo']}"
                      alt="${(realm.attributes['brandTenantName']!realm.displayName!'')}"
                      class="wc-brand-logo"
                  />
                <#else>
                  <span class="wc-brand-name">${(realm.attributes['brandTenantName']!realm.displayName!'')}</span>
                </#if>
            </div>
          </header>
          <main class="${properties.kcLoginMain!}">
            <div class="${properties.kcLoginMainHeader!}">
              <h1 class="${properties.kcLoginMainTitle!}" id="kc-page-title"><#nested "header"></h1>
                <#if realm.internationalizationEnabled && locale.supported?size gt 1>
                  <div class="${properties.kcLoginMainHeaderUtilities!}">
                    <div class="${properties.kcInputClass!}">
                      <select
                          aria-label="${msg("languages")}"
                          id="login-select-toggle"
                          onchange="if (this.value) window.location.href=this.value"
                      >
                          <#list locale.supported?sort_by("label") as l>
                            <option
                                value="${l.url}"
                                    ${(l.languageTag == locale.currentLanguageTag)?then('selected','')}
                            >
                                ${l.label}
                            </option>
                          </#list>
                      </select>
                      <span class="${properties.kcFormControlUtilClass}">
              <span class="${properties.kcFormControlToggleIcon!}">
                <svg
                    class="pf-v5-svg"
                    viewBox="0 0 320 512"
                    fill="currentColor"
                    aria-hidden="true"
                    role="img"
                    width="1em"
                    height="1em"
                >
                  <path
                      d="M31.3 192h257.3c17.8 0 26.7 21.5 14.1 34.1L174.1 354.8c-7.8 7.8-20.5 7.8-28.3 0L17.2 226.1C4.6 213.5 13.5 192 31.3 192z"
                  >
                  </path>
                </svg>
              </span>
            </span>
                    </div>
                  </div>
                </#if>
            </div>
            <div class="${properties.kcLoginMainBody!}">
                <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                    <#if displayRequiredFields>
                      <div class="${properties.kcContentWrapperClass!}">
                        <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="${properties.kcInputHelperTextItemTextClass!}">
                          <span class="${properties.kcInputRequiredClass!}">*</span> ${msg("requiredFields")}
                        </span>
                        </div>
                      </div>
                    </#if>
                <#else>
                    <#if displayRequiredFields>
                      <div class="${properties.kcContentWrapperClass!}">
                        <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="${properties.kcInputHelperTextItemTextClass!}">
                          <span class="${properties.kcInputRequiredClass!}">*</span> ${msg("requiredFields")}
                        </span>
                        </div>
                        <div class="${properties.kcFormClass} ${properties.kcContentWrapperClass}">
                            <#nested "show-username">
                            <@username />
                        </div>
                      </div>
                    <#else>
                      <div class="${properties.kcFormClass} ${properties.kcContentWrapperClass}">
                          <#nested "show-username">
                          <@username />
                      </div>
                    </#if>
                </#if>

                <#-- App-initiated actions should not see warning messages about the need to complete the action -->
                <#-- during login.                                                                               -->
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                  <div class="${properties.kcAlertClass!} pf-m-${(message.type = 'error')?then('danger', message.type)}">
                    <div class="${properties.kcAlertIconClass!}">
                        <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                        <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                        <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                        <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                    </div>
                    <span class="${properties.kcAlertTitleClass!} kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
                  </div>
                </#if>

                <#nested "form">

                <#if auth?has_content && auth.showTryAnotherWayLink()>
                  <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" novalidate="novalidate">
                    <input type="hidden" name="tryAnotherWay" value="on"/>
                    <a id="try-another-way" href="javascript:document.forms['kc-select-try-another-way-form'].requestSubmit()"
                       class="${properties.kcButtonSecondaryClass} ${properties.kcButtonBlockClass} ${properties.kcMarginTopClass}">
                        ${kcSanitize(msg("doTryAnotherWay"))?no_esc}
                    </a>
                  </form>
                </#if>

                <#if displayInfo>
                  <div id="kc-info" class="${properties.kcSignUpClass!}">
                    <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                        <#nested "info">
                    </div>
                  </div>
                </#if>
            </div>
            <div class="pf-v5-c-login__main-footer">
                <#nested "socialProviders">
            </div>
          </main>
        </div>
      </div>
      </body>
      </html>
    </#macro>
