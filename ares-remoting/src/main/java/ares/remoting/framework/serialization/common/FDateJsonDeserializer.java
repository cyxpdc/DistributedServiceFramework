package ares.remoting.framework.serialization.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.Date;

/**
 * @author pdc
 */
public class FDateJsonDeserializer extends JsonDeserializer<Date> {

    static final String PATTERN = "yyyy-MM-dd HH:mm:ss";
    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern(PATTERN);


    @Override
    public Date deserialize(JsonParser gen, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String date = gen.getText();
        if (StringUtils.isEmpty(date)) {
            return null;
        }
        if (StringUtils.isNumeric(date)) {
            return new Date(Long.valueOf(date));
        }
        try {
            DateTime dt = DATE_TIME_FORMATTER.parseDateTime(date);
            return dt.toDate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
