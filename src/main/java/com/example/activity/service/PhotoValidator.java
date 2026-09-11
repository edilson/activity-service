package com.example.activity.service;

import com.example.activity.domain.InvalidActivityException;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Locale;

@Component
public class PhotoValidator {
    public String validate(String filename, byte[] bytes) {
        if (filename == null || filename.isBlank() || filename.length() > 255)
            throw new InvalidActivityException("Photo filename is required and must be at most 255 characters");
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new InvalidActivityException("Photo must contain between 1 byte and 10 MiB");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidActivityException("Photo must be a valid PNG or JPEG");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw new InvalidActivityException("Only PNG and JPEG photos are supported");
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000)
                    throw new InvalidActivityException("Photo exceeds 20 megapixels");
                reader.read(0); // Reject truncated or malformed image data before uploading.
                return "image/" + format;
            } finally { reader.dispose(); }
        } catch (InvalidActivityException e) { throw e; }
        catch (Exception e) { throw new InvalidActivityException("Photo is not a readable PNG or JPEG"); }
    }
}
