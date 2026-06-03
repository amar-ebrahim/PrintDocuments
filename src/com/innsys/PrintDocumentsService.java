package com.innsys;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * BAW Java Integration entry point — PDF filling only, no FileNet dependency.
 *
 * BAW handles all FileNet operations (retrieve template, store result).
 * This class only fills PDF form fields and returns the result.
 *
 * Configure the Java Integration activity:
 *   Class  : com.innsys.PrintDocumentsService
 *   Method : fillPdf
 *
 * Data Mapping:
 *   Input  base64PdfTemplate (String) -> Parameter 1 (String)
 *   Input  serializedFields  (String) -> Parameter 2 (String)
 *   Output Return Value      (String) -> tw.local.base64FilledPdf
 */
public class PrintDocumentsService {

    private PrintDocumentsService() {}

    /**
     * Fills PDF form fields and returns the filled PDF as a Base64 string.
     *
     * @param base64PdfTemplate  Base64-encoded bytes of the blank PDF template
     * @param serializedFields   Tab-delimited key\tvalue pairs, one per line
     *                           (built in BAW script from listOf.KeyValuePair)
     * @return                   Base64-encoded bytes of the filled PDF
     */
    public static String fillPdf(String base64PdfTemplate,
                                 String serializedFields) throws Exception {

        long t0 = System.currentTimeMillis();

        // Decode input PDF
        byte[] pdfBytes = Base64.getDecoder().decode(base64PdfTemplate.trim());

        // Parse field mappings
        List<KeyValuePair> fields = deserializeFields(serializedFields);
        System.out.println("fillPdf: fields=" + fields.size()
                           + " pdfSize=" + pdfBytes.length + " bytes");

        // Fill the PDF
        byte[] filledBytes = TamplateServiceWS.generatePDF(
                new ByteArrayInputStream(pdfBytes), fields);


        long t1 = System.currentTimeMillis();
        System.out.println("fillPdf: done in " + (t1 - t0) + " ms"
                           + " outputSize=" + filledBytes.length + " bytes");

        // Return as Base64 so BAW can handle it as a String
        return Base64.getEncoder().encodeToString(filledBytes);
    }

    // ── Field deserialization ─────────────────────────────────────────────────

    private static List<KeyValuePair> deserializeFields(String s) {
        List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
        if (s == null || s.trim().isEmpty()) return pairs;
        for (String line : s.split("\n")) {
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            if (tab >= 0) {
                String key   = line.substring(0, tab).trim();
                String value = line.substring(tab + 1);
                if (!key.isEmpty()) pairs.add(new KeyValuePair(key, value));
            }
        }
        return pairs;
    }
}
