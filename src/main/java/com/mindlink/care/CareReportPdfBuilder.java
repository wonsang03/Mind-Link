package com.mindlink.care;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * AI 종합 보고서 → PDF 직렬화.
 * - OpenPDF(librepdf) 사용, CJK 내장 폰트 'HYSMyeongJo-Medium' 으로 한글 출력
 *   (OpenPDF 는 iText 4 호환으로 한국어 CJK 폰트를 자체 번들한다)
 * - 폰트 로드 실패 시 영문 헬베티카로 폴백
 */
@Component
public class CareReportPdfBuilder {

    private static final Logger log = LoggerFactory.getLogger(CareReportPdfBuilder.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String CRISIS_SUPPORT_LINE =
            "24시간 자살예방 상담: 1393   |   정신건강 위기 상담: 1577-0199   |   긴급: 119";

    public byte[] build(CareReport report, String userName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 72, 72);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new FooterEvent(loadKoreanFont(8.5f, Font.NORMAL)));
        doc.open();

        Font titleFont = loadKoreanFont(20f, Font.BOLD);
        Font subFont = loadKoreanFont(10f, Font.NORMAL);
        Font sectionFont = loadKoreanFont(12f, Font.BOLD);
        Font bodyFont = loadKoreanFont(11f, Font.NORMAL);
        Font noteFont = loadKoreanFont(9f, Font.ITALIC);

        Paragraph title = new Paragraph("마음이음 — 나를 위한 편지", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6f);
        doc.add(title);

        Paragraph subtitle = new Paragraph(
                "수신: " + (userName != null && !userName.isBlank() ? userName : "당신")
                        + "    /    생성일: "
                        + (report.getCreatedAt() != null ? report.getCreatedAt().format(DATE_FMT) : ""),
                subFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20f);
        doc.add(subtitle);

        // 위기 안내 박스
        if (report.getRiskLevel() == CareReport.RiskLevel.CRISIS) {
            Paragraph crisisHeader = new Paragraph("긴급 상담 안내", sectionFont);
            crisisHeader.setSpacingAfter(4f);
            doc.add(crisisHeader);
            Paragraph crisisLine = new Paragraph(CRISIS_SUPPORT_LINE, bodyFont);
            crisisLine.setSpacingAfter(14f);
            doc.add(crisisLine);
        }

        // 본문 — 단락별로 출력 (편지 내 \n\n 으로 단락 구분)
        Paragraph letterHeader = new Paragraph("편지", sectionFont);
        letterHeader.setSpacingAfter(6f);
        doc.add(letterHeader);

        String body = report.getLetterBody() != null ? report.getLetterBody() : "";
        for (String para : body.split("\\n\\s*\\n")) {
            String cleaned = para.replace("\r", "").trim();
            if (cleaned.isBlank()) continue;
            Paragraph p = new Paragraph(cleaned, bodyFont);
            p.setLeading(bodyFont.getSize() * 1.55f);
            p.setSpacingAfter(10f);
            doc.add(p);
        }

        // 주제
        String themes = report.getThemes();
        if (themes != null && !themes.isBlank()) {
            Paragraph th = new Paragraph("주제: " + themes, noteFont);
            th.setSpacingBefore(8f);
            doc.add(th);
        }

        // 면책 안내
        Paragraph disc = new Paragraph(
                "본 편지는 마음이음의 AI 가 생성한 참고용 메시지이며 전문 상담을 대체하지 않습니다. "
                        + "지속적인 어려움이 있다면 상담소 찾기 메뉴 또는 가까운 정신건강 전문가의 도움을 받아 보세요.",
                noteFont);
        disc.setSpacingBefore(16f);
        doc.add(disc);

        doc.close();
        try {
            out.flush();
        } catch (Exception ignored) {}
        return out.toByteArray();
    }

    /** 파일명 헬퍼 */
    public String suggestedFileName(CareReport report) {
        String day = report.getCreatedAt() != null
                ? report.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : "00000000";
        return "mindlink-letter-" + day + "-" + report.getId() + ".pdf";
    }

    private static Font loadKoreanFont(float size, int style) {
        try {
            BaseFont bf = BaseFont.createFont("HYSMyeongJo-Medium", "UniKS-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, style);
        } catch (Exception e) {
            log.warn("Korean CJK font load failed, falling back to Helvetica: {}", e.getMessage());
            return new Font(Font.HELVETICA, size, style);
        }
    }

    /** 페이지 푸터 — 페이지 번호 + 면책 */
    private static class FooterEvent extends PdfPageEventHelper {
        private final Font footerFont;

        FooterEvent(Font font) { this.footerFont = font; }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                String text = "마음이음 · 참고용 자료 · 전문 상담을 대체하지 않습니다   "
                        + writer.getPageNumber();
                Phrase phrase = new Phrase(text, footerFont);
                com.lowagie.text.pdf.ColumnText.showTextAligned(
                        writer.getDirectContent(), Element.ALIGN_CENTER, phrase,
                        (doc.right() + doc.left()) / 2, doc.bottom() - 20, 0);
            } catch (Exception ignored) {}
        }
    }
}
