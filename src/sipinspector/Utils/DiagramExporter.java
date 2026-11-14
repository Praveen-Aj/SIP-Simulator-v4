/*
 * Diagram Exporter
 * Exports a rendered diagram image to PNG or a minimal single-page PDF.
 * Zero external dependencies (pure JDK): PNG via ImageIO, PDF via an
 * embedded-JPEG image XObject on a page sized to the image.
 */
package sipinspector.Utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class DiagramExporter {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    /** Write the image as a lossless PNG. */
    public static void exportPng(BufferedImage img, File out) throws IOException {
        if (!ImageIO.write(img, "png", out)) {
            throw new IOException("No PNG writer available in this JRE.");
        }
    }

    /**
     * Write the image as a minimal one-page PDF. The image is JPEG-encoded and
     * embedded; the page is sized to the image (1 px = 1 pt at 72 dpi) so the
     * whole call flow fits on a single page with no down-scaling.
     */
    public static void exportPdf(BufferedImage img, File out) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();

        ByteArrayOutputStream jpgBuf = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "jpg", jpgBuf)) {
            throw new IOException("No JPEG writer available in this JRE.");
        }
        byte[] jpeg = jpgBuf.toByteArray();

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        writeAscii(pdf, "%PDF-1.4\n");
        // Binary marker comment (tells tools the file contains binary data).
        pdf.write(new byte[]{(byte) 0x25, (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, (byte) '\n'}, 0, 6);

        // 1: Catalog
        offsets.add(pdf.size());
        writeAscii(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // 2: Pages
        offsets.add(pdf.size());
        writeAscii(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        // 3: Page
        offsets.add(pdf.size());
        writeAscii(pdf, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "]"
                + " /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");

        // 4: Image XObject (embedded JPEG)
        offsets.add(pdf.size());
        writeAscii(pdf, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length
                + " >>\nstream\n");
        pdf.write(jpeg, 0, jpeg.length);
        writeAscii(pdf, "\nendstream\nendobj\n");

        // 5: Content stream (scale unit image square to full page size)
        byte[] content = ("q\n" + w + " 0 0 " + h + " 0 0 cm\n/Im0 Do\nQ\n").getBytes(LATIN1);
        offsets.add(pdf.size());
        writeAscii(pdf, "5 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        pdf.write(content, 0, content.length);
        writeAscii(pdf, "endstream\nendobj\n");

        // Cross-reference table
        int xrefPos = pdf.size();
        int size = offsets.size() + 1; // + free object 0
        writeAscii(pdf, "xref\n0 " + size + "\n");
        writeAscii(pdf, "0000000000 65535 f \n");
        for (int off : offsets) {
            writeAscii(pdf, String.format("%010d 00000 n \n", off));
        }
        writeAscii(pdf, "trailer\n<< /Size " + size + " /Root 1 0 R >>\nstartxref\n" + xrefPos + "\n%%EOF\n");

        try (OutputStream os = new FileOutputStream(out)) {
            byte[] bytes = pdf.toByteArray();
            os.write(bytes, 0, bytes.length);
        }
    }

    private static void writeAscii(ByteArrayOutputStream os, String s) {
        byte[] b = s.getBytes(LATIN1);
        os.write(b, 0, b.length);
    }
}
