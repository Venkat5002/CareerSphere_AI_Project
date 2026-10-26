package com.careersphere.ai;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pulls plain text out of a resume file. Scanned (image-only) PDFs come back nearly empty. */
@Component
public class ResumeTextExtractor {

    public String extract(Path file, String fileName) {
        String name = fileName.toLowerCase();
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                    return clean(new PDFTextStripper().getText(doc));
                }
            }
            if (name.endsWith(".docx")) {
                try (InputStream in = Files.newInputStream(file);
                     XWPFDocument doc = new XWPFDocument(in);
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    return clean(ex.getText());
                }
            }
            if (name.endsWith(".doc")) {
                try (InputStream in = Files.newInputStream(file);
                     HWPFDocument doc = new HWPFDocument(in);
                     WordExtractor ex = new WordExtractor(doc)) {
                    return clean(ex.getText());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read text from the resume.", e);
        }
        return "";
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(' ', ' ').replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
