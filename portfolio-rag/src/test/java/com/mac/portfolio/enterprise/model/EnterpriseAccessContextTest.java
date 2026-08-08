package com.mac.portfolio.enterprise.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseAccessContextTest {

    @Test
    void onlySupportedDemoRolesBecomeRetrievalContexts() {
        assertThat(EnterpriseAccessContext.from("engineering", "tenant-a"))
                .isEqualTo(new EnterpriseAccessContext("engineering", "tenant-a", "engineering"));
        assertThat(EnterpriseAccessContext.from("not-a-role", "tenant-a").role()).isEqualTo("public");
        assertThat(EnterpriseAccessContext.from("ADMIN", "").isAdmin()).isTrue();
    }
}
