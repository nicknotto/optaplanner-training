/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.training.workerrostering.persistence;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.optaplanner.persistence.common.api.domain.solution.SolutionFileIO;
import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.RosterParametrization;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;

public class WorkerRosteringSolutionFileIO implements SolutionFileIO<Roster> {

    private static final String SKILL_SHEET_NAME = "Skills";

    @Override
    public String getInputFileExtension() {
        return "xlsx";
    }

    @Override
    public String getOutputFileExtension() {
        return "xlsx";
    }

    @Override
    public Roster read(File inputSolutionFile) {
        Workbook workbook;
        try (InputStream in = new BufferedInputStream(new FileInputStream(inputSolutionFile))) {
            workbook = new XSSFWorkbook(in);
            return new RosterReader(workbook).readRoster();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed reading inputSolutionFile ("
                    + inputSolutionFile + ") to create a roster.", e);
        }
    }

    private static  class RosterReader {

        private final Workbook workbook;

        public RosterReader(Workbook workbook) {
            this.workbook = workbook;
        }

        public Roster readRoster() {
            RosterParametrization rosterParametrization = new RosterParametrization();
            List<Skill> skillList = createSkillList();
            List<Spot> spotList = createSpotList(skillList);
            List<TimeSlot> timeSlotList = createTimeSlotList();
            List<Employee> employeeList = createEmployeeList(skillList);
            List<ShiftAssignment> shiftAssignmentList = createShiftAssignmentList(spotList, timeSlotList, employeeList);
            return new Roster(rosterParametrization,
                    skillList, spotList, timeSlotList, employeeList,
                    shiftAssignmentList);
        }

        private List<Skill> createSkillList() {
            Sheet sheet = workbook.getSheet(SKILL_SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("The workbook does not contain a sheet with the name "
                        + SKILL_SHEET_NAME + ".");
            }
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                throw new IllegalStateException("The sheet (" + SKILL_SHEET_NAME + ") has no header data at row 1.");
            }
            if (!headerRow.getCell(0).getStringCellValue().equals("Name")) {
                throw new IllegalStateException("The sheet (" + SKILL_SHEET_NAME + ") does not have the right content.");
            }

            List<Skill> skillList = new ArrayList<>(sheet.getLastRowNum() - 2);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Cell cell = row.getCell(0);
                String name = cell.getStringCellValue();
                skillList.add(new Skill(name));
            }
            return skillList;
        }

        private List<Spot> createSpotList(List<Skill> skillList) {
            return null; // TODO
        }

        private List<TimeSlot> createTimeSlotList() {
            return null; // TODO
        }

        private List<Employee> createEmployeeList(List<Skill> skillList) {
            return null; // TODO
        }

        private List<ShiftAssignment> createShiftAssignmentList(List<Spot> spotList, List<TimeSlot> timeSlotList, List<Employee> employeeList) {
            return null; // TODO
        }

    }

    @Override
    public void write(Roster roster, File outputSolutionFile) {
        Workbook workbook = new RosterWriter(roster).writeWorkbook();
        try (FileOutputStream out = new FileOutputStream(outputSolutionFile)) {
            workbook.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing outputSolutionFile ("
                    + outputSolutionFile + ") for roster (" + roster + ").", e);
        }
    }

    private static class RosterWriter {

        private final Roster roster;

        private final Workbook workbook;
        private final CellStyle headerStyle;
        private final CellStyle unexistingStyle;

        public RosterWriter(Roster roster) {
            this.roster = roster;
            workbook = new XSSFWorkbook();
            headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            unexistingStyle = workbook.createCellStyle();
            unexistingStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            unexistingStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
        }

        public Workbook writeWorkbook() {
            Map<Pair<Spot, TimeSlot>, ShiftAssignment> spotMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.toMap(
                    shiftAssignment -> Pair.of(shiftAssignment.getSpot(), shiftAssignment.getTimeSlot()),
                    shiftAssignment -> shiftAssignment));
            Map<Pair<Employee, TimeSlot>, List<ShiftAssignment>> employeeMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.groupingBy(
                    shiftAssignment -> Pair.of(shiftAssignment.getEmployee(), shiftAssignment.getTimeSlot()),
                    Collectors.toList()));

            writeGridSheet("Spot roster", new String[]{"Name", "Required skill"}, roster.getSpotList(), (Row row, Spot spot) -> {
                row.createCell(0).setCellValue(spot.getName());
                row.createCell(1).setCellValue(spot.getRequiredSkill().getName());
            }, (Cell cell, Pair<Spot, TimeSlot> pair) -> {
                ShiftAssignment shiftAssignment = spotMap.get(pair);
                if (shiftAssignment == null) {
                    cell.setCellStyle(unexistingStyle);
                    return;
                }
                Employee employee = shiftAssignment.getEmployee();
                if (employee == null) {
                    cell.setCellValue("?");
                    return;
                }
                cell.setCellValue(employee.getName());
            });
            writeGridSheet("Employee roster", new String[]{"Name"}, roster.getEmployeeList(), (Row row, Employee employee) -> {
                row.createCell(0).setCellValue(employee.getName());
            }, (Cell cell, Pair<Employee, TimeSlot> pair) -> {
                List<ShiftAssignment> shiftAssignmentList = employeeMap.get(pair);
                if (shiftAssignmentList == null) {
                    return;
                }
                cell.setCellValue(shiftAssignmentList.stream()
                        .map((shiftAssignment) -> shiftAssignment.getSpot().getName())
                        .collect(Collectors.joining(",")));
            });
            writeListSheet("Employees", new String[]{"Name", "Skills"}, roster.getEmployeeList(), (Row row, Employee employee) -> {
                row.createCell(0).setCellValue(employee.getName());
                row.createCell(1).setCellValue(employee.getSkillSet().stream()
                        .map(Skill::getName).collect(Collectors.joining(",")));
            });
            writeListSheet("Timeslots", new String[]{"Start", "End"}, roster.getTimeSlotList(), (Row row, TimeSlot timeSlot) -> {
                row.createCell(0).setCellValue(timeSlot.getStartDateTime().toString());
                row.createCell(1).setCellValue(timeSlot.getEndDateTime().toString());
            });
            writeListSheet(SKILL_SHEET_NAME, new String[]{"Name"}, roster.getSkillList(), (Row row, Skill skill) -> {
                row.createCell(0).setCellValue(skill.getName());
            });
            return workbook;
        }

        private <E> Sheet writeListSheet(String sheetName, String[] headerTitles, List<E> elementList,
                BiConsumer<Row, E> rowConsumer) {
            Sheet sheet = workbook.createSheet(sheetName);
            sheet.setDefaultColumnWidth(20);
            int rowNumber = 0;
            sheet.createRow(rowNumber++); // Leave empty
            Row headerRow = sheet.createRow(rowNumber++);
            int columnNumber = 0;
            for (String headerTitle : headerTitles) {
                Cell cell = headerRow.createCell(columnNumber);
                cell.setCellValue(headerTitle);
                cell.setCellStyle(headerStyle);
                columnNumber++;
            }
            sheet.createFreezePane(1, 2);
            for (E element : elementList) {
                Row row = sheet.createRow(rowNumber);
                rowConsumer.accept(row, element);
                rowNumber++;
            }
            return sheet;
        }

        private <E> void writeGridSheet(String sheetName, String[] headerTitles, List<E> rowElementList,
                BiConsumer<Row, E> rowConsumer,
                BiConsumer<Cell, Pair<E, TimeSlot>> cellConsumer) {
            Sheet sheet = writeListSheet(sheetName, headerTitles, rowElementList, rowConsumer);
            sheet.setDefaultColumnWidth(5);
            sheet.createFreezePane(headerTitles.length, 2);
            Row higherHeaderRow = sheet.getRow(0);
            Row lowerHeaderRow = sheet.getRow(1);
            int columnNumber = headerTitles.length;
            for (TimeSlot timeSlot : roster.getTimeSlotList()) {
                if (timeSlot.getStartDateTime().getHour() == 6) {
                    Cell cell = higherHeaderRow.createCell(columnNumber);
                    // TODO use formatter
                    cell.setCellValue(timeSlot.getStartDateTime().getDayOfWeek().toString().substring(0, 3)
                            + " " + timeSlot.getStartDateTime().toLocalDate().toString());
                    cell.setCellStyle(headerStyle);
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, columnNumber, columnNumber + 2));
                }
                Cell cell = lowerHeaderRow.createCell(columnNumber);
                cell.setCellValue(timeSlot.getStartDateTime().toLocalTime().toString());
                cell.setCellStyle(headerStyle);
                columnNumber++;
            }
            int rowNumber = 2;
            for (E rowElement : rowElementList) {
                Row row = sheet.getRow(rowNumber);
                columnNumber = headerTitles.length;
                for (TimeSlot timeSlot : roster.getTimeSlotList()) {
                    Cell cell = row.createCell(columnNumber);
                    cellConsumer.accept(cell, Pair.of(rowElement, timeSlot));
                    columnNumber++;
                }
                rowNumber++;
            }
        }

    }

}
