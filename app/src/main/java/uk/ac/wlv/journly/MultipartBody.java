package uk.ac.wlv.journly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Builds a multipart/form-data request body (text fields and files)
public class MultipartBody {

    private final String boundary = "JournlyBoundary" + System.currentTimeMillis();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public MultipartBody addField(String name, String value) {
        return addPart(name, null, "text/plain; charset=UTF-8",
                value.getBytes(StandardCharsets.UTF_8));
    }

    public MultipartBody addJson(String name, String json) {
        return addPart(name, null, "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    public MultipartBody addFile(String name, String filename,
                                 String contentType, byte[] data) {
        return addPart(name, filename, contentType, data);
    }

    private MultipartBody addPart(String name, String filename,
                                  String contentType, byte[] data) {
        try {
            StringBuilder header = new StringBuilder();
            header.append("--").append(boundary).append("\r\n");
            header.append("Content-Disposition: form-data; name=\"")
                    .append(name).append("\"");
            if (filename != null) {
                header.append("; filename=\"").append(filename).append("\"");
            }
            header.append("\r\n");
            header.append("Content-Type: ").append(contentType).append("\r\n\r\n");
            out.write(header.toString().getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    // The finished body, with the closing boundary added
    public byte[] toBytes() {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            result.write(out.toByteArray());
            result.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.toByteArray();
    }
}