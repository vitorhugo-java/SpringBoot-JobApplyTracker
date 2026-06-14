package com.jobtracker.unit.mcp;

import com.jobtracker.dto.gdrive.BaseInformationContentResponse;
import com.jobtracker.dto.gdrive.BaseInformationResponse;
import com.jobtracker.mcp.tools.McpBaseInformationTools;
import com.jobtracker.service.BaseInformationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpBaseInformationToolsTest {

    @Mock
    private BaseInformationService baseInformationService;

    @InjectMocks
    private McpBaseInformationTools tools;

    @Test
    void listBaseInformation_delegatesToService() {
        List<BaseInformationResponse> expected = List.of(new BaseInformationResponse(
                UUID.randomUUID(), "about-me.md", "MARKDOWN", "https://drive/file", LocalDateTime.now()));
        when(baseInformationService.listBaseInformation()).thenReturn(expected);

        assertThat(tools.listBaseInformation(null)).isSameAs(expected);
        verify(baseInformationService).listBaseInformation();
    }

    @Test
    void getBaseInformationContent_parsesUuidAndDelegates() {
        UUID infoId = UUID.randomUUID();
        BaseInformationContentResponse expected =
                new BaseInformationContentResponse(infoId, "about-me.md", "MARKDOWN", "Senior Java Developer");
        when(baseInformationService.getBaseInformationContent(infoId)).thenReturn(expected);

        BaseInformationContentResponse result = tools.getBaseInformationContent(null, infoId.toString());

        assertThat(result).isSameAs(expected);
        verify(baseInformationService).getBaseInformationContent(infoId);
    }
}
