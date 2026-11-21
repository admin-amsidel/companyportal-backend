/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Monthly payslips: drafts made from the salary, published to the employee, with year to date and a PDF. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"test.context=payslips", "app.payslip.company-address=Kakkanad, Kochi, Kerala"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayslipTest {

    @Autowired
    private MockMvc mockMvc;

    private String owner;
    private long dw;
    private Map<String, Integer> components;
    private int seq;

    @BeforeAll
    void setUp() throws Exception {
        owner = token(changePin(token(login("owner", "Ams@2026")), "Ams@2026", "Own#2026"));
        List<Integer> brandIds = JsonPath.read(body(mockMvc.perform(get("/api/brands").header("Authorization", auth(owner)))),
                "$[?(@.code == 'DW')].id");
        dw = brandIds.get(0);
        List<Map<String, Object>> all = JsonPath.read(body(mockMvc.perform(get("/api/payroll/components")
                .header("Authorization", auth(owner)))), "$");
        components = all.stream().collect(Collectors.toMap(c -> (String) c.get("code"), c -> (Integer) c.get("id")));
    }

    @Test
    void draftsArePrivateUntilPublished() throws Exception {
        String[] emp = employee("2025-09-09");
        salary(emp[0], "2025-09-09");

        String result = body(json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":9,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)));
        String id = payslipId(emp[0], 2025, 9);
        // joined Tue 9th: the 22 days to the 30th, Sundays included as in a full month (Q1); earnings and the fixed
        // deductions = monthly / 30 x 22, reimbursements and the typed tax in full
        mockMvc.perform(get("/api/payroll/payslips/" + id).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.periodLabel").value("September 2025"))
                .andExpect(jsonPath("$.daysInMonth").value(30))
                .andExpect(jsonPath("$.workingDays").value(30))
                .andExpect(jsonPath("$.employedDays").value(22))
                .andExpect(jsonPath("$.paidDays").value(22))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(71704.00)))
                .andExpect(jsonPath("$.lines[?(@.code == 'EPF')].amount").value(hasItem(1320.00)))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].fullAmount").value(hasItem(97778.00)))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].amount").value(hasItem(24957.00)))
                .andExpect(jsonPath("$.company.address").value("Kakkanad, Kochi, Kerala"));
        // generating again makes nothing new
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":9,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(0));

        // the employee does not see the draft
        mockMvc.perform(get("/api/my-pay").param("fy", "2025").header("Authorization", auth(emp[1])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payslips.length()").value(0));
        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(emp[1]))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payroll/payslips").param("year", "2025").param("month", "9").header("Authorization", auth(emp[1])))
                .andExpect(status().isForbidden());

        // payroll puts the full month back (the payslip's figures) and saves as a draft, then publishes
        String lines = fullMonthLines(id);
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-09-30\",\"lopDays\":0,\"lines\":" + lines + ",\"publish\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.money.net").value(214945.00))
                .andExpect(jsonPath("$.netInWords").value("Indian Rupee Two Lakh Fourteen Thousand Nine Hundred Forty-Five Only"));
        // crediting a draft is refused
        json(put("/api/payroll/payslips/" + id + "/payment"), owner, "{\"paidOn\":\"2025-09-30\",\"mode\":\"BANK\"}")
                .andExpect(status().isConflict());
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-09-30\",\"lopDays\":0,\"lines\":" + lines + ",\"publish\":true}")
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.paidOn").value(nullValue()));
        // published, not credited yet: still hidden from the employee
        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(emp[1]))).andExpect(status().isNotFound());
        json(put("/api/payroll/payslips/" + id + "/payment"), owner, "{\"paidOn\":\"2099-01-01\",\"mode\":\"BANK\"}")
                .andExpect(status().isBadRequest());
        json(put("/api/payroll/payslips/" + id + "/payment"), owner,
                "{\"paidOn\":\"2025-10-01\",\"mode\":\"BANK\",\"reference\":\"UTR123456789\",\"remarks\":\" Paid with the October batch \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidOn").value("2025-10-01"))
                .andExpect(jsonPath("$.paymentMode").value("BANK"))
                .andExpect(jsonPath("$.paymentRef").value("UTR123456789"))
                .andExpect(jsonPath("$.paymentRemarks").value("Paid with the October batch"));
        mockMvc.perform(get("/api/payroll/payslips").param("year", "2025").param("month", "9").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.rows[?(@.employee.id == " + emp[0] + ")].paymentMode").value(hasItem("BANK")))
                .andExpect(jsonPath("$.credited").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(emp[1])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.money.net").value(214945.00))
                .andExpect(jsonPath("$.paymentRef").value("UTR123456789"))
                // payroll's remarks stay with payroll
                .andExpect(jsonPath("$.paymentRemarks").value(nullValue()))
                .andExpect(jsonPath("$.editable").value(false));
        byte[] pdf = mockMvc.perform(get("/api/my-pay/payslips/" + id + "/pdf").header("Authorization", auth(emp[1])))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("Payslip-2025-09-")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5000);

        // published: locked until moved back to draft
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-09-30\",\"lopDays\":1,\"lines\":[],\"publish\":false}")
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/payroll/payslips/" + id).header("Authorization", auth(owner))).andExpect(status().isConflict());
        // credited: cannot go back to draft until the credit is undone
        mockMvc.perform(post("/api/payroll/payslips/" + id + "/unpublish").header("Authorization", auth(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/payroll/payslips/" + id + "/payment").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.paidOn").value(nullValue()));
        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(emp[1]))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/payroll/payslips/" + id + "/unpublish").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void yearToDateAndTheEmployeesDashboard() throws Exception {
        String[] emp = employee("2025-04-01");
        salary(emp[0], "2025-04-01");
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":4,\"employeeIds\":[" + emp[0] + "]}");
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":5,\"employeeIds\":[" + emp[0] + "]}");
        String april = payslipId(emp[0], 2025, 4);
        String may = payslipId(emp[0], 2025, 5);
        // two unpaid days in May (30 working days): earnings and the fixed deductions for 28, worked out again
        String mayLines = body(mockMvc.perform(get("/api/payroll/payslips/" + may).header("Authorization", auth(owner))));
        json(put("/api/payroll/payslips/" + may), owner, "{\"payDate\":\"2025-05-31\",\"lopDays\":2,\"lines\":[],\"publish\":false}")
                .andExpect(jsonPath("$.paidDays").value(28)).andExpect(jsonPath("$.lopDays").value(2));
        assertThat(mayLines).contains("\"paidDays\":30");

        json(post("/api/payroll/payslips/publish"), owner, "{\"ids\":[" + april + "," + may + "]}")
                .andExpect(jsonPath("$.published").value(2));
        mockMvc.perform(get("/api/my-pay").param("fy", "2025").header("Authorization", auth(emp[1])))
                .andExpect(jsonPath("$.payslips.length()").value(0));
        json(post("/api/payroll/payslips/payments"), owner, "{\"ids\":[" + april + "," + may + "],\"paidOn\":\"2025-06-01\",\"mode\":\"CASH\",\"reference\":\"VCH-0601\",\"remarks\":\"Cash voucher\"}")
                .andExpect(jsonPath("$.credited").value(2));
        mockMvc.perform(get("/api/payroll/payslips/" + april).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.paymentRef").value("VCH-0601")).andExpect(jsonPath("$.paymentRemarks").value("Cash voucher"));
        // May's year to date = April + May (May's BASIC: 97,778 / 30 x 28 = 91,259)
        mockMvc.perform(get("/api/my-pay/payslips/" + may).header("Authorization", auth(emp[1])))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].ytd").value(hasItem(49914.00)))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].ytd").value(hasItem(189037.00)));

        mockMvc.perform(get("/api/my-pay").param("fy", "2025").header("Authorization", auth(emp[1])))
                .andExpect(jsonPath("$.fyLabel").value("FY 2025-26"))
                .andExpect(jsonPath("$.salary.set").value(true))
                // April 2,38,652 + May 2,22,742 (28 days) + ten months to come at 2,38,652
                .andExpect(jsonPath("$.dashboard.expectedGross").value(2847914.00))
                .andExpect(jsonPath("$.dashboard.earnedGross").value(461394.00))
                // income tax 24,957 a month (typed) + professional tax 1,250 (May: 1,167 for 28 days)
                .andExpect(jsonPath("$.dashboard.taxesPaid").value(52331.00))
                .andExpect(jsonPath("$.dashboard.expectedTax").value(314401.00))
                .andExpect(jsonPath("$.dashboard.balanceGross").value(2386520.00))
                .andExpect(jsonPath("$.dashboard.monthsPaid").value(2))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.months[0].label").value("Apr"))
                // May: 2,22,742 less the deductions for 28 days (27,897) + 4,400 reimbursed
                .andExpect(jsonPath("$.months[1].net").value(199245.00))
                .andExpect(jsonPath("$.months[2].payslipId").value(nullValue()))
                .andExpect(jsonPath("$.payslips[0].label").value("May 2025"));

        // undo the credit and back to draft: the employee no longer sees it; a draft can be deleted
        mockMvc.perform(delete("/api/payroll/payslips/" + may + "/payment").header("Authorization", auth(owner)));
        mockMvc.perform(post("/api/payroll/payslips/" + may + "/unpublish").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/my-pay/payslips/" + may).header("Authorization", auth(emp[1]))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/payroll/payslips/" + may).header("Authorization", auth(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payroll/payslips").param("year", "2025").param("month", "5").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.rows[?(@.employee.id == " + emp[0] + ")].payslipId").value(hasItem(nullValue())));
    }

    @Test
    void anotherPersonsPayslipStaysPrivate() throws Exception {
        String[] a = employee("2025-04-01");
        String[] b = employee("2025-04-01");
        salary(a[0], "2025-04-01");
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":6,\"employeeIds\":[" + a[0] + "]}");
        String id = payslipId(a[0], 2025, 6);
        json(post("/api/payroll/payslips/publish"), owner, "{\"ids\":[" + id + "]}");
        json(put("/api/payroll/payslips/" + id + "/payment"), owner, "{\"paidOn\":\"2025-07-01\",\"mode\":\"UPI\"}");
        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(a[1]))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMode").value("UPI"));
        mockMvc.perform(get("/api/my-pay/payslips/" + id).header("Authorization", auth(b[1]))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/my-pay/payslips/" + id + "/pdf").header("Authorization", auth(b[1]))).andExpect(status().isNotFound());
    }

    @Test
    void theBrandsDetailsAndLogoOnThePayslip() throws Exception {
        // a separate brand, so the other tests keep the plain DW brand
        String brand = String.valueOf((Object) JsonPath.read(body(json(post("/api/brands"), owner,
                "{\"code\":\"HX\",\"name\":\"Acme Home\"}").andExpect(status().isCreated())), "$.id"));
        String details = """
                {"code":"HX","name":"Acme Home Appliances","address":"Kakkanad, Kochi, Kerala 682030",
                 "launchedDate":"2024-06-01","owners":"Sibin James ,Anu Thomas,, Ravi Kumar ","gstNumber":"32abcde1234f1z5",
                 "category":"Home Appliances","bankAccount":"50200011122233","ifsc":"hdfc0004321","upi":"Acme.Home@OKHDFC",
                 "contactNumber":"+91 484 2400 400","email":"Home@Example.com"}""";
        json(put("/api/brands/" + brand), owner, details).andExpect(status().isOk())
                .andExpect(jsonPath("$.owners").value("Sibin James, Anu Thomas, Ravi Kumar"))
                .andExpect(jsonPath("$.gstNumber").value("32ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.category").value("Home Appliances"))
                .andExpect(jsonPath("$.launchedDate").value("2024-06-01"))
                .andExpect(jsonPath("$.bankAccountNumber").value("50200011122233"))
                .andExpect(jsonPath("$.ifscCode").value("HDFC0004321"))
                .andExpect(jsonPath("$.upiId").value("acme.home@okhdfc"))
                .andExpect(jsonPath("$.contactNumber").value("+91 48424 00400"))
                .andExpect(jsonPath("$.email").value("home@example.com"));
        json(put("/api/brands/" + brand), owner, details.replace("Home@Example.com", "not an email")).andExpect(status().isBadRequest());
        json(put("/api/brands/" + brand), owner, details.replace("hdfc0004321", "HDFC123")).andExpect(status().isBadRequest());
        json(put("/api/brands/" + brand), owner, details.replace("Acme.Home@OKHDFC", "not-a-upi")).andExpect(status().isBadRequest());
        json(put("/api/brands/" + brand), owner, details.replace("32abcde1234f1z5", "12345")).andExpect(status().isBadRequest());

        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart("/api/brands/" + brand + "/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png", png))
                        .header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.logoUpdatedAt").isNotEmpty());
        mockMvc.perform(multipart("/api/brands/" + brand + "/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", auth(owner)))
                .andExpect(status().isBadRequest());
        // the logo is public
        mockMvc.perform(get("/api/brands/" + brand + "/logo")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        // a person of that brand: the payslip shows the brand
        String userId = "ps.home" + (++seq);
        String id = String.valueOf((Object) JsonPath.read(body(mockMvc.perform(post("/api/employees").header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Home Person","userId":"%s","password":"ab@123","role":"EMPLOYEE","brandId":%s,
                         "phone":"+91 98470 00000","joinedDate":"2025-04-01"}""".formatted(userId, brand)))
                .andExpect(status().isCreated())), "$.id"));
        salary(id, "2025-04-01");
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":7,\"employeeIds\":[" + id + "]}");
        String slip = payslipId(id, 2025, 7);
        mockMvc.perform(get("/api/payroll/payslips/" + slip).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.company.name").value("Acme Home Appliances"))
                .andExpect(jsonPath("$.company.address").value("Kakkanad, Kochi, Kerala 682030"))
                .andExpect(jsonPath("$.company.gstNumber").value("32ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.company.logoUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.company.phone").value("+91 48424 00400"))
                .andExpect(jsonPath("$.company.email").value("home@example.com"));
        byte[] pdf = mockMvc.perform(get("/api/payroll/payslips/" + slip + "/pdf").header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // only owners change brands
        String[] emp = employee("2025-04-01");
        json(put("/api/brands/" + brand), emp[1], details).andExpect(status().isForbidden());
    }

    @Test
    void bankDetailsFromTheEmployeeScreen() throws Exception {
        String[] a = employee("2025-04-01");
        String[] b = employee("2025-04-01");
        json(put("/api/employees/" + a[0] + "/bank-details"), owner,
                "{\"pan\":\"pqrst6789k\",\"bankAccount\":\"1234567890\",\"ifsc\":\"sbin0004567\",\"upi\":\"Slip@OKSBI\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pan").value("PQRST6789K"))
                .andExpect(jsonPath("$.ifsc").value("SBIN0004567"))
                .andExpect(jsonPath("$.upi").value("slip@oksbi"))
                .andExpect(jsonPath("$.canEdit").value(true));
        json(put("/api/employees/" + a[0] + "/bank-details"), owner, "{\"ifsc\":\"SBI123\"}").andExpect(status().isBadRequest());
        // the person sees their own, read-only; not someone else's; and cannot change them
        mockMvc.perform(get("/api/auth/me/bank-details").header("Authorization", auth(a[1])))
                .andExpect(jsonPath("$.bankAccount").value("1234567890")).andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(get("/api/employees/" + a[0] + "/bank-details").header("Authorization", auth(b[1])))
                .andExpect(status().isForbidden());
        json(put("/api/employees/" + a[0] + "/bank-details"), a[1], "{\"upi\":\"x@ok\"}").andExpect(status().isForbidden());
        // an admin opening their own, and another admin's: admins do not manage admins, which once ended in a 500
        String[] adm = employee("2025-04-01", "ADMIN");
        String[] adm2 = employee("2025-04-01", "ADMIN");
        mockMvc.perform(get("/api/auth/me/bank-details").header("Authorization", auth(adm[1])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(get("/api/employees/" + adm2[0] + "/bank-details").header("Authorization", auth(adm[1])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canEdit").value(false));
        // the payroll salary page shows the same details
        mockMvc.perform(get("/api/payroll/salaries/" + a[0]).param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.employee.upi").value("slip@oksbi"));
        // the general employee record does not carry them
        mockMvc.perform(get("/api/employees/" + a[0]).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.pan").doesNotExist()).andExpect(jsonPath("$.bankAccount").doesNotExist());
    }

    @Test
    void workingDaysOfTheMonthCanBeChanged() throws Exception {
        String[] emp = employee("2025-04-01");
        salary(emp[0], "2025-04-01");
        mockMvc.perform(get("/api/payroll/salaries/" + emp[0]).param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.workingDays").value(30));
        // the lists carry the brand's name for the Brand filter
        mockMvc.perform(get("/api/payroll/salaries").param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.rows[*].employee.brandName", hasItem("Acme Doors & Windows")));
        // June made with 25 days payable (of the salary's 30): 25 paid days at the salary's day rate (PORTAL-20)
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":6,\"workingDays\":25,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(1));
        String id = payslipId(emp[0], 2025, 6);
        mockMvc.perform(get("/api/payroll/payslips/" + id).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.workingDays").value(25)).andExpect(jsonPath("$.paidDays").value(25))
                .andExpect(jsonPath("$.dayBasis").value(30))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(81482.00)));
        // then 22 days payable and one LOP day: 21 paid; LOP cannot pass the days, nor the days the salary's 30
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-06-30\",\"workingDays\":31,\"lopDays\":0,\"lines\":[],\"publish\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Days payable cannot be more than the salary's 30 working days a month"));
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-06-30\",\"workingDays\":22,\"lopDays\":1,\"lines\":[],\"publish\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(22)).andExpect(jsonPath("$.paidDays").value(21));
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-06-30\",\"workingDays\":22,\"lopDays\":23,\"lines\":[],\"publish\":false}")
                .andExpect(status().isBadRequest());
        // recalculating from the salary keeps the month's days and LOP: BASIC = 97,778 / 30 x 21
        mockMvc.perform(post("/api/payroll/payslips/" + id + "/recalculate").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.workingDays").value(22)).andExpect(jsonPath("$.paidDays").value(21))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(68445.00)));
        // the salary's own working days apply to new payslips
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":7,\"employeeIds\":[" + emp[0] + "]}");
        mockMvc.perform(get("/api/payroll/payslips/" + payslipId(emp[0], 2025, 7)).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.workingDays").value(30));
    }

    @Test
    void incomeTaxIsWorkedOutAgainForTheMonth() throws Exception {
        String[] emp = employee("2025-04-01");
        // new regime: 1,50,000 a month is 12,567 tax a month (as on the salary)
        String lines = "[" + line("BASIC", "150000") + "," + line("PT", "200") + "," + line("TDS", "0") + "]";
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner,
                "{\"effectiveFrom\":\"2025-04-01\",\"taxRegime\":\"NEW\",\"lines\":%s}".formatted(lines))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].monthly").value(hasItem(12567.0)));
        // June: April and May made no payslip, so they count nothing (paid outside the portal); ten months of 1,50,000 =
        // 15 lakh - 75,000 = 14.25 lakh: 20,000 + 40,000 + 33,750 = 93,750 + cess 3,750 = 97,500, over June to March = 9,750
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":6,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(1));
        String id = payslipId(emp[0], 2025, 6);
        mockMvc.perform(get("/api/payroll/payslips/" + id).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].amount").value(hasItem(9750.0)))
                .andExpect(jsonPath("$.tax.regime").value("NEW")).andExpect(jsonPath("$.tax.monthsLeft").value(9));
        // April and May were paid elsewhere: 3 lakh, with 25,134 tax deducted; the year is 18 lakh again, 1,50,800 less
        // 25,134 over ten months = 12,567
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner,
                "{\"effectiveFrom\":\"2025-04-01\",\"outsideIncome\":300000,\"outsideTax\":25134,\"lines\":%s}".formatted(lines))
                // deducted already: the 25,134 outside, and June's draft (9,750); April and May show as paid outside
                .andExpect(jsonPath("$.outsideIncome").value(300000.0)).andExpect(jsonPath("$.tax.taxBefore").value(34884.0))
                .andExpect(jsonPath("$.plan[0].source").value("OUTSIDE")).andExpect(jsonPath("$.plan[2].source").value("PAYSLIP"));
        String view = body(mockMvc.perform(post("/api/payroll/payslips/" + id + "/recalculate").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].amount").value(hasItem(12567.0)))
                .andExpect(jsonPath("$.tax.yearTax").value(150800.0)).andExpect(jsonPath("$.tax.taxBefore").value(25134.0)));

        // half the month unpaid: less pay, less tax; a tax typed in its line is not kept
        List<Map<String, Object>> ls = JsonPath.read(view, "$.lines");
        String half = ls.stream().map(l -> "{\"id\":%s,\"amount\":%s}".formatted(l.get("id"),
                        "BASIC".equals(l.get("code")) ? "75000" : "TDS".equals(l.get("code")) ? "99999" : l.get("amount")))
                .collect(Collectors.joining(",", "[", "]"));
        double preview = ((Number) JsonPath.read(body(json(post("/api/payroll/payslips/" + id + "/preview"), owner,
                        "{\"lopDays\":13,\"lines\":%s}".formatted(half))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paidDays").value(17.0))), "$.tax.monthTax")).doubleValue();
        String saved = body(json(put("/api/payroll/payslips/" + id), owner,
                "{\"payDate\":\"2025-06-30\",\"lopDays\":13,\"lines\":%s,\"publish\":false}".formatted(half)).andExpect(status().isOk()));
        List<Double> tds = JsonPath.read(saved, "$.lines[?(@.code == 'TDS')].amount");
        assertThat(tds.getFirst()).isEqualTo(preview).isLessThan(12567.0).isGreaterThan(0.0);

        // typed in (Manual): what payroll types stays
        String manual = "[" + line("BASIC", "150000") + "," + line("PT", "200") + "," + line("TDS", "5000") + "]";
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner,
                "{\"effectiveFrom\":\"2025-04-01\",\"taxRegime\":\"MANUAL\",\"lines\":%s}".formatted(manual)).andExpect(status().isOk());
        mockMvc.perform(post("/api/payroll/payslips/" + id + "/recalculate").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.lines[?(@.code == 'TDS')].amount").value(hasItem(5000.0)))
                .andExpect(jsonPath("$.tax").doesNotExist());
    }

    @Test
    void fewerDaysPayablePayProportionallyLessAtTheSalarysDayRate() throws Exception {
        // PORTAL-20: a 26-day salary of 17,100 (a day = 657.69); 13 days payable with 1 LOP = 12 days, about 7,892
        String[] emp = employee("2025-04-01");
        String lines = "[" + String.join(",", line("BASIC", "15000"), line("DA", "1000"), line("HRA", "500"), line("LTA", "500"),
                line("SPECIAL", "100")) + "]";
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner, "{\"effectiveFrom\":\"2025-04-01\",\"workingDays\":26,\"lines\":%s}"
                .formatted(lines)).andExpect(status().isOk());
        // made with the dialog's old 30 for everyone: never more than the salary's own 26, so a full month pays in full
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":11,\"workingDays\":30,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(1));
        String id = payslipId(emp[0], 2025, 11);
        mockMvc.perform(get("/api/payroll/payslips/" + id).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.workingDays").value(26)).andExpect(jsonPath("$.dayBasis").value(26))
                .andExpect(jsonPath("$.money.gross").value(17100.0));
        // 13 days payable, 1 LOP: 12 of the salary's 26 days, each component 12 / 26 of its rate
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-11-30\",\"workingDays\":13,\"lopDays\":1,\"lines\":[],\"publish\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidDays").value(12.0))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(6923.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'DA')].amount").value(hasItem(462.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'SPECIAL')].amount").value(hasItem(46.0)))
                .andExpect(jsonPath("$.money.gross").value(7893.0));
        // the preview says the same
        json(post("/api/payroll/payslips/" + id + "/preview"), owner, "{\"workingDays\":13,\"lopDays\":1,\"reset\":true}")
                .andExpect(jsonPath("$.dayBasis").value(26)).andExpect(jsonPath("$.money.gross").value(7893.0));
        // more days payable than the salary's 26 is refused
        json(put("/api/payroll/payslips/" + id), owner, "{\"payDate\":\"2025-11-30\",\"workingDays\":27,\"lopDays\":0,\"lines\":[],\"publish\":false}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRaiseMidYearIsPaidForItsShareOfTheMonth() throws Exception {
        String[] emp = employee("2025-04-01");
        String from = "[" + line("BASIC", "30000") + "]";
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner, "{\"effectiveFrom\":\"2025-04-01\",\"lines\":%s}".formatted(from))
                .andExpect(status().isOk());
        // a raise from 16 October: a second revision of the year
        String raised = body(json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner,
                        "{\"effectiveFrom\":\"2025-10-16\",\"lines\":[%s]}".formatted(line("BASIC", "40000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions.length()").value(2))
                .andExpect(jsonPath("$.revisions[0].until").value("2025-10-15"))
                .andExpect(jsonPath("$.effectiveFrom").value("2025-10-16"))
                // April to mid-October at 30,000, then 40,000: 6 x 30,000 + 35,161 + 5 x 40,000
                .andExpect(jsonPath("$.totals.yearGross").value(415161.0)));
        Integer raise = JsonPath.read(raised, "$.revisionId");
        // October: 15 days at 30,000 and 16 at 40,000 = (4,50,000 + 6,40,000) / 31 = 35,161
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":10,\"employeeIds\":[" + emp[0] + "]}");
        mockMvc.perform(get("/api/payroll/payslips/" + payslipId(emp[0], 2025, 10)).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(35161.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].fullAmount").value(hasItem(40000.0)));
        mockMvc.perform(get("/api/payroll/salaries").param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.rows[?(@.employee.id == " + emp[0] + ")].revisions").value(hasItem(2)));
        // two revisions cannot start the same day; one can be removed (October's draft first, made with it)
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner,
                "{\"revisionId\":%s,\"effectiveFrom\":\"2025-04-01\",\"lines\":%s}".formatted(raise, from))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/payroll/payslips/" + payslipId(emp[0], 2025, 10)).header("Authorization", auth(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/payroll/salaries/" + emp[0]).param("fy", "2025").param("revisionId", String.valueOf(raise))
                .header("Authorization", auth(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payroll/salaries/" + emp[0]).param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.revisions.length()").value(1)).andExpect(jsonPath("$.totals.yearGross").value(360000.0));
    }

    @Test
    void epfEsiAndProfessionalTaxAreWorkedOut() throws Exception {
        String[] emp = employee("2025-04-01");
        String lines = "[" + line("BASIC", "14000") + "," + line("DA", "2000") + "," + line("HRA", "4000") + "]";
        // gross 20,000: EPF 12% of the 15,000 ceiling = 1,800; ESI 0.75% = 150; professional tax on a half year of 1.2 lakh = 1,000, a sixth a month
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner, ("{\"effectiveFrom\":\"2025-04-01\",\"pfApplies\":true,"
                        + "\"esiApplies\":true,\"ptApplies\":true,\"lines\":%s}").formatted(lines))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[?(@.code == 'EPF')].monthly").value(hasItem(1800.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'EPF')].auto").value(hasItem(true)))
                .andExpect(jsonPath("$.lines[?(@.code == 'ESI')].monthly").value(hasItem(150.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'PT')].monthly").value(hasItem(167.0)));
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2025,\"month\":4,\"employeeIds\":[" + emp[0] + "]}");
        String april = payslipId(emp[0], 2025, 4);
        // ten unpaid days: paid 20 of 30, gross 13,333; EPF on the Basic + DA paid (10,667) = 1,280; ESI = 100 (rounded up)
        json(put("/api/payroll/payslips/" + april), owner, "{\"payDate\":\"2025-04-30\",\"lopDays\":10,\"lines\":[],\"publish\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.money.gross").value(13333.0))
                .andExpect(jsonPath("$.lines[?(@.code == 'EPF')].amount").value(hasItem(1280.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'ESI')].amount").value(hasItem(100.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'PT')].amount").value(hasItem(167.0)));
        // EPF on the full wage, and a gross over ESI's 21,000: EPF 12% of 22,000 = 2,640, no ESI
        String more = "[" + line("BASIC", "20000") + "," + line("DA", "2000") + "," + line("HRA", "4000") + "]";
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner, ("{\"effectiveFrom\":\"2025-04-01\",\"pfFullWage\":true,"
                        + "\"lines\":%s}").formatted(more))
                .andExpect(jsonPath("$.lines[?(@.code == 'EPF')].monthly").value(hasItem(2640.0)))
                .andExpect(jsonPath("$.lines[?(@.code == 'ESI')].monthly").value(hasItem(0.0)));
    }

    @Test
    void theExitDateEndsThePay() throws Exception {
        String[] emp = employee("2025-04-01", "EMPLOYEE", "\"exitDate\":\"2026-01-15\"");
        salary(emp[0], "2025-04-01");
        // the salary cannot start after the exit date; the year is paid April to January
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=2025"), owner, "{\"effectiveFrom\":\"2026-02-01\",\"lines\":[%s]}"
                .formatted(line("BASIC", "1000"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/payroll/salaries/" + emp[0]).param("fy", "2025").header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.totals.monthsInYear").value(10)).andExpect(jsonPath("$.plan[10].source").value("NONE"));
        // January up to the 15th: 15 of 31 days, scaled to 30 = 15 (14.5 rounded)
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2026,\"month\":1,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(1));
        mockMvc.perform(get("/api/payroll/payslips/" + payslipId(emp[0], 2026, 1)).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.employedDays").value(15)).andExpect(jsonPath("$.employee.exitDate").value("2026-01-15"));
        // nothing after
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":2026,\"month\":2,\"employeeIds\":[" + emp[0] + "]}")
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void approvedUnpaidLeaveBecomesLossOfPay() throws Exception {
        String[] emp = employee("2025-04-01");
        // two working days of leave without pay in the leave plan in force (a Monday and Tuesday)
        String current = body(mockMvc.perform(get("/api/leave/me/current").header("Authorization", auth(emp[1]))));
        java.time.LocalDate until = java.time.LocalDate.parse(JsonPath.read(current, "$.validUntil"));
        java.time.LocalDate monday = java.time.LocalDate.parse(JsonPath.read(current, "$.today"))
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        while (monday.plusDays(1).getMonthValue() != monday.getMonthValue()) {
            monday = monday.plusWeeks(1);
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(!monday.plusDays(1).isAfter(until), "the leave plan in force ends too soon");
        List<Map<String, Object>> types = JsonPath.read(body(mockMvc.perform(get("/api/leave/types").header("Authorization", auth(owner)))),
                "$[?(@.code == 'LWP')]");
        String leave = body(json(post("/api/leave/me/requests"), emp[1], "{\"leaveTypeId\":%s,\"fromDate\":\"%s\",\"toDate\":\"%s\",\"halfDay\":false}"
                .formatted(types.getFirst().get("id"), monday, monday.plusDays(1))).andExpect(status().isCreated()));
        if ("PENDING".equals(JsonPath.read(leave, "$.status"))) {
            json(post("/api/leave/requests/" + JsonPath.read(leave, "$.id") + "/approve"), owner, "{}").andExpect(status().isOk());
        }
        int fy = monday.getMonthValue() >= 4 ? monday.getYear() : monday.getYear() - 1;
        json(put("/api/payroll/salaries/" + emp[0] + "?fy=" + fy), owner, "{\"effectiveFrom\":\"%d-04-01\",\"lines\":[%s]}"
                .formatted(fy, line("BASIC", "30000"))).andExpect(status().isOk());
        json(post("/api/payroll/payslips/generate"), owner, "{\"year\":%d,\"month\":%d,\"employeeIds\":[%s]}"
                .formatted(monday.getYear(), monday.getMonthValue(), emp[0])).andExpect(jsonPath("$.created").value(1));
        mockMvc.perform(get("/api/payroll/payslips/" + payslipId(emp[0], monday.getYear(), monday.getMonthValue())).header("Authorization", auth(owner)))
                .andExpect(jsonPath("$.lopDays").value(2.0)).andExpect(jsonPath("$.unpaidLeaveDays").value(2.0))
                .andExpect(jsonPath("$.paidDays").value(28.0))
                .andExpect(jsonPath("$.lines[?(@.code == 'BASIC')].amount").value(hasItem(28000.0)));
    }

    @Test
    void indianFormatting() {
        assertThat(com.companyportal.backend.service.RupeesInWords.of(new java.math.BigDecimal("12500050.50")))
                .isEqualTo("Indian Rupee One Crore Twenty-Five Lakh Fifty and Fifty Paise Only");
    }

    // ---- helpers ----

    /** The September 2025 payslip as the salary. */
    private void salary(String employeeId, String from) throws Exception {
        String lines = "[" + String.join(",",
                line("BASIC", "97778"), line("DA", "24444"), line("HRA", "39111"), line("LTA", "3667"), line("SPECIAL", "73652"),
                line("EPF", "1800"), line("LWF", "100"), line("TDS", "24957"), line("PT", "1250"),
                line("FUEL", "2933"), line("PHONE", "1467")) + "]";
        json(put("/api/payroll/salaries/" + employeeId + "?fy=2025"), owner,
                "{\"effectiveFrom\":\"%s\",\"notes\":\"\",\"lines\":%s,\"pan\":\"abcde1234f\",\"bankAccount\":\"50100012345662\",\"ifsc\":\"hdfc0001234\",\"upi\":\"Anu.T@OKHDFC\"}"
                        .formatted(from, lines))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.pan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.employee.ifsc").value("HDFC0001234"))
                .andExpect(jsonPath("$.employee.upi").value("anu.t@okhdfc"));
    }

    /** Every line of the payslip at its full monthly amount. */
    private String fullMonthLines(String payslipId) throws Exception {
        String view = body(mockMvc.perform(get("/api/payroll/payslips/" + payslipId).header("Authorization", auth(owner))));
        List<Map<String, Object>> lines = JsonPath.read(view, "$.lines");
        return lines.stream().map(l -> "{\"id\":%s,\"amount\":%s}".formatted(l.get("id"), l.get("fullAmount")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String payslipId(String employeeId, int year, int month) throws Exception {
        String list = body(mockMvc.perform(get("/api/payroll/payslips").param("year", String.valueOf(year))
                .param("month", String.valueOf(month)).header("Authorization", auth(owner))));
        List<Integer> ids = JsonPath.read(list, "$.rows[?(@.employee.id == " + employeeId + ")].payslipId");
        return String.valueOf(ids.get(0));
    }

    private String line(String code, String monthly) {
        return "{\"componentId\":%d,\"monthly\":%s}".formatted(components.get(code), monthly);
    }

    /** {id, token} of a new employee of DW. */
    private String[] employee(String joined) throws Exception {
        return employee(joined, "EMPLOYEE");
    }

    private String[] employee(String joined, String role) throws Exception {
        return employee(joined, role, null);
    }

    /** more: further fields of the employee, e.g. "exitDate":"2026-01-15". */
    private String[] employee(String joined, String role, String more) throws Exception {
        String userId = "ps.emp" + (++seq);
        String json = """
                {"name":"Slip %d","userId":"%s","password":"ab@123","role":"%s","brandId":%d,
                 "phone":"+91 98470 00000","joinedDate":"%s"%s}
                """.formatted(seq, userId, role, dw, joined, more == null ? "" : "," + more);
        String id = String.valueOf((Object) JsonPath.read(body(mockMvc.perform(post("/api/employees").header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())), "$.id"));
        return new String[]{id, token(changePin(token(login(userId, "ab@123")), "ab@123", "cd@456"))};
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
                               String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String userId, String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\",\"password\":\"%s\"}".formatted(userId, pin)));
    }

    private ResultActions changePin(String token, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/change-password").header("Authorization", auth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.token");
    }

    private static String auth(String token) {
        return "asd " + token;
    }
}
