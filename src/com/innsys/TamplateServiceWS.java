package com.innsys;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;

public class TamplateServiceWS {

    // ── Font cache ────────────────────────────────────────────────────────────
    // Loaded once from the classpath at class-init time (~200 KB).
    // Each call to generatePDF wraps these bytes in a ByteArrayInputStream
    // so PDFBox can embed the font without hitting disk/classpath again.
    private static final byte[] ARABIC_FONT_BYTES = loadFontBytes();

    private static byte[] loadFontBytes() {
        InputStream is = TamplateServiceWS.class.getClassLoader()
                .getResourceAsStream("NotoNaskhArabic-Regular.ttf");
        if (is == null) {
            System.out.println("WARNING: NotoNaskhArabic-Regular.ttf not found in classpath");
            return null;
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(204_800);
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toByteArray();
        } catch (IOException e) {
            System.out.println("WARNING: Could not load Arabic font: " + e.getMessage());
            return null;
        } finally {
            try { is.close(); } catch (IOException ignore) {}
        }
    }

    // ── PDF processing ────────────────────────────────────────────────────────

    static byte[] generatePDF(InputStream pdfStream,
                               List<KeyValuePair> fieldValues)
            throws IOException {
        PDDocument pdf = null;
        try {
            pdf = PDDocument.load(pdfStream);

            PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
            if (form == null) {
                throw new IOException("PDF contains no AcroForm fields.");
            }

            PDResources defaultResources = form.getDefaultResources();
            if (defaultResources == null) {
                defaultResources = new PDResources();
                form.setDefaultResources(defaultResources);
            }

            // Embed the Arabic font using the pre-loaded byte cache
            COSName fontName = null;
            if (ARABIC_FONT_BYTES != null) {
                PDType0Font unicodeFont = PDType0Font.load(
                        pdf, new ByteArrayInputStream(ARABIC_FONT_BYTES), false);
                fontName = defaultResources.add(unicodeFont);
            }

            // Index all fields by fully-qualified name for O(1) lookup
            Map<String, PDField> fieldsByName = new HashMap<String, PDField>();
            for (PDField field : form.getFieldTree()) {
                String name = field.getFullyQualifiedName();
                if (name != null) fieldsByName.put(name, field);
            }

            for (KeyValuePair kv : fieldValues) {
                if (kv == null) continue;
                String name  = kv.getKey();
                String value = kv.getValue();

                PDField field = fieldsByName.get(name);
                if (field == null) continue;

                if (field instanceof PDCheckBox) {
                    applyCheckboxValue((PDCheckBox) field, value);
                    field.setReadOnly(true);
                    continue;
                }

                if (value != null) {
                    boolean isArabic = containsArabic(value);
                    String finalValue = isArabic ? shapeArabic(value) : value;

                    if (isArabic && fontName != null && field instanceof PDVariableText) {
                        PDVariableText vt = (PDVariableText) field;
                        float fontSize = parseFontSize(vt.getDefaultAppearance(), 10f);
                        fontSize = clampFontSize(fontSize, fieldHeight(field));
                        vt.setDefaultAppearance("/" + fontName.getName() + " " + fontSize + " Tf 0 g");
                        vt.setQ(2); // right-align Arabic
                    }

                    field.setValue(finalValue);
                    field.setReadOnly(true);
                }
            }

            form.setNeedAppearances(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdf.save(baos);
            return baos.toByteArray();

        } finally {
            if (pdf != null) try { pdf.close(); } catch (IOException ignore) {}
        }
    }

    // ── Font size helpers ─────────────────────────────────────────────────────

    private static float parseFontSize(String da, float fallback) {
        if (da == null) return fallback;
        String[] tokens = da.trim().split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("Tf".equals(tokens[i + 1])) {
                try { return Float.parseFloat(tokens[i]); } catch (NumberFormatException ignore) {}
            }
        }
        return fallback;
    }

    private static float fieldHeight(PDField field) {
        List<PDAnnotationWidget> widgets = field.getWidgets();
        if (widgets != null && !widgets.isEmpty()) {
            PDRectangle rect = widgets.get(0).getRectangle();
            if (rect != null) return rect.getHeight();
        }
        return -1f;
    }

    private static float clampFontSize(float fontSize, float fieldHeight) {
        final float MIN = 6.0f;
        final float MAX_RATIO = 0.80f;
        if (fieldHeight > 0) {
            float usable = Math.max(fieldHeight - 4f, fieldHeight);
            float max = usable * MAX_RATIO;
            if (fontSize > max) fontSize = max;
        }
        return Math.max(fontSize, MIN);
    }

    // ── Arabic helpers ────────────────────────────────────────────────────────

    private static boolean containsArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF) ||
                (c >= 0x0750 && c <= 0x077F) ||
                (c >= 0x08A0 && c <= 0x08FF)) return true;
        }
        return false;
    }

    private static String shapeArabic(String logicalText) {
        if (logicalText == null || logicalText.isEmpty()) return logicalText;
        try {
            ArabicShaping shaper = new ArabicShaping(ArabicShaping.LETTERS_SHAPE);
            String shaped = shaper.shape(logicalText);
            return new StringBuilder(shaped).reverse().toString();
        } catch (ArabicShapingException e) {
            System.out.println("WARNING: ArabicShaping failed: " + e.getMessage());
            return logicalText;
        }
    }

    // ── Checkbox helper ───────────────────────────────────────────────────────

    private static void applyCheckboxValue(PDCheckBox cb, String value) throws IOException {
        if (value == null) { cb.unCheck(); return; }
        String v = value.trim().toLowerCase();
        boolean checked = "true".equals(v) || "yes".equals(v) || "y".equals(v)
                       || "on".equals(v)   || "1".equals(v)   || "x".equals(v)
                       || "checked".equals(v);
        if (checked) cb.check(); else cb.unCheck();
    }
}
