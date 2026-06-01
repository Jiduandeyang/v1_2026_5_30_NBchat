package com.example.chat.media;

import com.example.chat.common.Jdbc;
import com.example.chat.model.MediaFile;

import java.sql.Connection;
import java.sql.SQLException;

public class MediaDao {
    public MediaFile create(Connection connection, long ownerId, UploadKind kind, String originalName, String storedName,
                            String url, String contentType, long sizeBytes) throws SQLException {
        long id = Jdbc.insert(connection,
                "INSERT INTO media_files(owner_id,kind,original_name,stored_name,url,content_type,size_bytes) VALUES(?,?,?,?,?,?,?)",
                ps -> {
                    ps.setLong(1, ownerId);
                    ps.setString(2, kind.name());
                    ps.setString(3, originalName);
                    ps.setString(4, storedName);
                    ps.setString(5, url);
                    ps.setString(6, contentType);
                    ps.setLong(7, sizeBytes);
                });
        return new MediaFile(id, ownerId, kind.name(), originalName, url, contentType, sizeBytes);
    }
}
