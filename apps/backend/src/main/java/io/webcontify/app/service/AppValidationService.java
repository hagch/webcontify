package io.webcontify.app.service;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.app.repository.entity.AppDependencyEntity;
import io.webcontify.generated.model.AppProblem;
import io.webcontify.generated.model.AppProblemsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppValidationService {

    private final AppDependencyRepository dependencyRepository;
    private final AppService appService;

    public AppValidationService(AppDependencyRepository dependencyRepository, AppService appService) {
        this.dependencyRepository = dependencyRepository;
        this.appService = appService;
    }

    public AppProblemsResponse validate(UUID appId) {
        List<AppDependencyEntity> dependencies = dependencyRepository.findByAppId(appId);
        List<AppProblem> problems = new ArrayList<>();

        for (AppDependencyEntity dep : dependencies) {
            App depApp = appService.getById(dep.getDependsOnId());
            if (depApp.getStatus() != AppStatus.ACTIVE) {
                AppProblem problem = new AppProblem();
                problem.setSeverity(AppProblem.SeverityEnum.ERROR);
                problem.setMessage("Dependency '" + depApp.getName() + "' is inactive");
                problem.setResource("app:" + dep.getDependsOnId());
                problem.setFixAction("Activate or remove dependency");
                problems.add(problem);
            }
        }

        int errorCount = (int) problems.stream()
                .filter(p -> p.getSeverity() == AppProblem.SeverityEnum.ERROR)
                .count();
        int warningCount = (int) problems.stream()
                .filter(p -> p.getSeverity() == AppProblem.SeverityEnum.WARNING)
                .count();

        AppProblemsResponse response = new AppProblemsResponse();
        response.setItems(problems);
        response.setErrorCount(errorCount);
        response.setWarningCount(warningCount);
        return response;
    }

    public boolean hasErrors(UUID appId) {
        return validate(appId).getErrorCount() > 0;
    }
}
