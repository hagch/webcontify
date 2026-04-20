package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.app.repository.entity.AppDependencyEntity;
import io.webcontify.generated.model.AppProblemsResponse;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppValidationServiceTest {

    private static final UUID TEST_APP_ID = UUID.randomUUID();
    private static final UUID DEP_APP_ID = UUID.randomUUID();

    @Mock
    private AppDependencyRepository dependencyRepository;

    @Mock
    private AppService appService;

    private AppValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new AppValidationService(dependencyRepository, appService);
    }

    @Test
    void shouldReturnEmptyWhenNoProblems() {
        AppDependencyEntity depEntity = new AppDependencyEntity();
        depEntity.setAppId(TEST_APP_ID);
        depEntity.setDependsOnId(DEP_APP_ID);

        when(dependencyRepository.findByAppId(TEST_APP_ID)).thenReturn(List.of(depEntity));

        App depApp = new App();
        depApp.setId(DEP_APP_ID);
        depApp.setName("Active Dep");
        depApp.setStatus(AppStatus.ACTIVE);
        when(appService.getById(DEP_APP_ID)).thenReturn(depApp);

        AppProblemsResponse result = validationService.validate(TEST_APP_ID);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getErrorCount()).isZero();
        assertThat(result.getWarningCount()).isZero();
    }

    @Test
    void shouldReturnErrorWhenDependencyInactive() {
        AppDependencyEntity depEntity = new AppDependencyEntity();
        depEntity.setAppId(TEST_APP_ID);
        depEntity.setDependsOnId(DEP_APP_ID);

        when(dependencyRepository.findByAppId(TEST_APP_ID)).thenReturn(List.of(depEntity));

        App depApp = new App();
        depApp.setId(DEP_APP_ID);
        depApp.setName("Inactive Dep");
        depApp.setStatus(AppStatus.INACTIVE);
        when(appService.getById(DEP_APP_ID)).thenReturn(depApp);

        AppProblemsResponse result = validationService.validate(TEST_APP_ID);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getMessage()).contains("Inactive Dep");
        assertThat(result.getItems().get(0).getSeverity())
                .isEqualTo(io.webcontify.generated.model.AppProblem.SeverityEnum.ERROR);
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getWarningCount()).isZero();
    }

    @Test
    void shouldReturnNoErrorsForAppWithoutDependencies() {
        when(dependencyRepository.findByAppId(TEST_APP_ID)).thenReturn(Collections.emptyList());

        AppProblemsResponse result = validationService.validate(TEST_APP_ID);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getErrorCount()).isZero();
        assertThat(result.getWarningCount()).isZero();
        assertThat(validationService.hasErrors(TEST_APP_ID)).isFalse();
    }
}
