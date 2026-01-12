package com.ltnc.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExcelExporter {

    public static String exportStocktakeToExcel(List<Map<String, Object>> stocktakeData, Stage stage) {
        try {
            // Create workbook and sheet
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Kiểm kê");

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            int rowNum = 0;

            // Add title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("PHIẾU KIỂM KÊ TÀI SẢN");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 7));

            // Add empty row
            rowNum++;

            // Add timestamp
            Row dateRow = sheet.createRow(rowNum++);
            Cell dateCell = dateRow.createCell(0);
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            dateCell.setCellValue("Ngày kiểm kê: " + now.format(formatter));
            dateCell.setCellStyle(normalStyle);

            // Add empty row
            rowNum++;

            // Create header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"STT", "Mã tài sản", "Tên tài sản", "Loại", "Đơn vị", "Số lượng theo sổ", "Số lượng thực tế", "Chênh lệch"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Add data rows
            int rowIndex = 1;
            int dataRowNum = rowNum;
            for (Map<String, Object> row : stocktakeData) {
                Row dataRow = sheet.createRow(dataRowNum++);

                dataRow.createCell(0).setCellValue(rowIndex++);
                dataRow.createCell(1).setCellValue(row.get("id") != null ? row.get("id").toString() : "");
                dataRow.createCell(2).setCellValue(row.get("name") != null ? row.get("name").toString() : "");
                dataRow.createCell(3).setCellValue(row.get("asset_category") != null ? row.get("asset_category").toString() : "");
                dataRow.createCell(4).setCellValue(row.get("base_unit") != null ? row.get("base_unit").toString() : "");

                // Handle numbers
                Object bookQty = row.get("book_quantity");
                Object actualQty = row.get("actual_quantity");
                
                int bookQtyInt = 0;
                int actualQtyInt = 0;

                if (bookQty instanceof Integer) {
                    bookQtyInt = (Integer) bookQty;
                } else if (bookQty instanceof String) {
                    bookQtyInt = Integer.parseInt((String) bookQty);
                }

                if (actualQty instanceof Integer) {
                    actualQtyInt = (Integer) actualQty;
                } else if (actualQty instanceof String) {
                    actualQtyInt = Integer.parseInt((String) actualQty);
                }

                dataRow.createCell(5).setCellValue(bookQtyInt);
                dataRow.createCell(6).setCellValue(actualQtyInt);
                dataRow.createCell(7).setCellValue(actualQtyInt - bookQtyInt);

                // Apply style
                for (int i = 0; i < 8; i++) {
                    dataRow.getCell(i).setCellStyle(normalStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }

            // Show file chooser dialog
            String filePath = showFileChooserDialog(stage);
            if (filePath == null || filePath.isEmpty()) {
                workbook.close();
                return "CANCELLED";
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

            workbook.close();
            return filePath;

        } catch (IOException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private static String showFileChooserDialog(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn vị trí lưu file kiểm kê");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home") + "\\Downloads"));
        
        // Set default filename
        String defaultFileName = generateFileName();
        fileChooser.setInitialFileName(defaultFileName);
        
        // Add Excel file filter
        FileChooser.ExtensionFilter excelFilter = new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx");
        fileChooser.getExtensionFilters().add(excelFilter);

        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile != null) {
            String filePath = selectedFile.getAbsolutePath();
            
            // Ensure .xlsx extension
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }
            
            return filePath;
        }
        
        return null;
    }   
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private static String generateFileName() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return "Phieu_Kiem_Ke_" + now.format(formatter) + ".xlsx";
    }
}
