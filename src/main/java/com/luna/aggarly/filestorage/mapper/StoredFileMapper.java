package com.luna.aggarly.filestorage.mapper;

import com.luna.aggarly.filestorage.dto.response.StoredFileResponse;
import com.luna.aggarly.filestorage.dto.response.UploadResponse;
import com.luna.aggarly.filestorage.entity.StoredFile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoredFileMapper {
    StoredFileResponse toResponse(StoredFile storedFile);
    UploadResponse toUploadResponse(StoredFile storedFile);
}
