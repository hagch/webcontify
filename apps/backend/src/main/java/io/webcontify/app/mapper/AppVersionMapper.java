package io.webcontify.app.mapper;

import io.webcontify.app.model.AppVersion;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.generated.model.AppVersionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppVersionMapper {

    AppVersion toDomain(AppVersionEntity entity);

    AppVersionResponse toResponse(AppVersion domain);
}
