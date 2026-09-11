package com.example.activity.service;

import com.example.activity.domain.InvalidActivityException;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class PhotoValidatorTest {
    static byte[] png() throws Exception {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes); return bytes.toByteArray();
    }
    @Test void detectsTypeFromContentsRatherThanExtension() throws Exception {
        assertThat(new PhotoValidator().validate("photo.jpg", png())).isEqualTo("image/png");
    }
    @Test void rejectsInvalidOversizedAndMissingPhotos() {
        var validator = new PhotoValidator();
        for (byte[] bytes : new byte[][]{new byte[0], new byte[]{1, 2, 3}, new byte[10 * 1024 * 1024 + 1]})
            assertThatThrownBy(() -> validator.validate("photo.png", bytes)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> validator.validate(null, new byte[]{1})).isInstanceOf(InvalidActivityException.class);
    }
}
