package com.example.chat.media;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.MediaFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.InputStream;

@Path("/media")
@Produces(MediaType.APPLICATION_JSON)
public class MediaResource {
    private final MediaService mediaService = new MediaService();

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ApiResponse<MediaFile> upload(
            @Context HttpServletRequest request,
            @FormDataParam("kind") String kind,
            @FormDataParam("file") InputStream inputStream,
            @FormDataParam("file") FormDataContentDisposition disposition,
            @FormDataParam("file") FormDataBodyPart bodyPart) {
        long userId = SessionSupport.requireUserId(request);
        UploadKind uploadKind = UploadKind.valueOf(kind);
        long size = disposition == null ? 0 : disposition.getSize();
        String name = disposition == null ? "upload.bin" : disposition.getFileName();
        String contentType = bodyPart == null || bodyPart.getMediaType() == null ? null : bodyPart.getMediaType().toString();
        return ApiResponse.ok(mediaService.save(userId, uploadKind, name, contentType, inputStream, size));
    }
}
