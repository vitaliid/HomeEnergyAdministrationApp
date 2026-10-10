package org.example.validation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.Normalizer;

/**
 * Normalizes texts to Unicode Normalization Form C and then validates them against
 * the chosen DIN 91379 datatype (din-norm-91379-datatypes.xsd). Normalization always happens first,
 * so that e.g. "e" + U+0301 is accepted as the precomposed "é".
 */
@Slf4j
@Service
public class Din91379TextValidation {

    private static final String SCHEMA_LOCATION = "xsd/din91379-wrapper.xsd";
    private static final String ROOT_NAMESPACE = "urn:example:din91379-validation";

    private Schema schema;

    @PostConstruct
    public void init() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "jar,file");
            schema = factory.newSchema(new StreamSource(new ClassPathResource(SCHEMA_LOCATION).getURL().toExternalForm()));
        } catch (SAXException e) {
            throw new IllegalStateException("Cannot load DIN 91379 schema", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Normalizes the text to Unicode Normalization Form C and then validates it against the given
     * DIN 91379 datatype. The order matters: e.g. "e" + U+0301 is accepted as the precomposed "é".
     */
    public String normalizeAndValidate(String text, Din91379Type type) {
        if (text == null) {
            return null;
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);

        if (!conformsTo(normalized, type)) {
            log.warn("DIN 91379 {} validation failed for a text of length {}", type, normalized.length());
            throw new BusinessException(ErrorCode.INVALID_FIELD_FORMAT);
        }

        return normalized;
    }

    private boolean conformsTo(String text, Din91379Type type) {
        String element = type.element();
        String xml = "<" + element + " xmlns=\"" + ROOT_NAMESPACE + "\">" + escape(text) + "</" + element + ">";
        try {
            schema.newValidator().validate(new StreamSource(new StringReader(xml)));
            return true;
        } catch (SAXException e) {
            // also covers characters that are not even legal in XML (e.g. control characters)
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
