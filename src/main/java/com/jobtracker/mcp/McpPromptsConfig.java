package com.jobtracker.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Registers MCP prompt templates. The Spring AI MCP server autoconfiguration collects beans of
 * type List&lt;McpServerFeatures.SyncPromptSpecification&gt; and exposes them via the
 * prompts/list and prompts/get protocol endpoints.
 * <p>
 * Prompts are parameterized instructions that tell an MCP client (e.g. Claude) exactly which
 * tools to call and in which order to accomplish a guided workflow.
 */
@Configuration
public class McpPromptsConfig {

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> mcpPrompts() {
        return List.of(
                intakeVacancyPrompt(),
                prepareNewApplicationPrompt(),
                tailorResumePrompt(),
                summarizePipelinePrompt()
        );
    }

    /**
     * Full autonomous "vacancy intake" workflow. Given a pasted job description / link /
     * recruiter message, drives the end-to-end flow: read the real resume (source of truth),
     * pick the base template by language, detect placeholders, generate ATS-friendly values,
     * create the application, generate the filled resume, and optionally draft a recruiter email.
     */
    private McpServerFeatures.SyncPromptSpecification intakeVacancyPrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "intake_vacancy",
                "Executa o workflow autonomo completo de candidatura a partir de uma vaga colada "
                        + "(analise, leitura do CV real, geracao do CV, registro e rascunho de email)",
                List.of(
                        new McpSchema.PromptArgument("vacancyContent",
                                "Descricao da vaga, link, mensagem do recrutador ou post do LinkedIn", true)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String vacancy = getArg(req, "vacancyContent", "(cole o conteudo da vaga aqui)");

            String text = """
                    Voce e meu assistente de candidaturas para engenharia de software, operando via os MCPs
                    "Job Apply Tracker - MCP", "Google Drive" e "Gmail". Fale SEMPRE comigo em PT-BR.
                    Conteudo gerado para o CV deve estar no IDIOMA DA VAGA.

                    Execute o workflow completo de intake de vaga de forma AUTONOMA, sem esperar confirmacoes
                    intermediarias. Pare apenas para perguntas genuinamente necessarias (ver Passo 5).

                    === VAGA ===
                    %s
                    === FIM DA VAGA ===

                    Siga rigorosamente os passos abaixo, nesta ordem:

                    PASSO 1 - Analisar a vaga
                    Extrair: vacancyName (cargo), organization (empresa), vacancyLink (URL se houver),
                    stack tecnologica exigida, senioridade, idioma da vaga, e nome/email do recrutador se presentes.

                    PASSO 2 - Ler meu curriculo REAL (fonte de verdade — obrigatorio antes de gerar qualquer conteudo)
                    - Chamar listApplications e selecionar o fileId de um CV JA GERADO em candidatura anterior,
                      no MESMO idioma da vaga. NUNCA usar um template como fonte de dados.
                    - Ler esse arquivo com Google Drive:read_file_content.
                    - Se nao houver CV anterior: Google Drive:search_files com
                      "title contains 'curriculo' or title contains 'resume' or title contains 'cv'" e ler o mais relevante.
                    - Se ainda assim nao encontrar: me perguntar o nome/link do arquivo de CV no Drive.
                    Extrair: experiencias, stack real, projetos, formacao/certificacoes, conquistas mensuraveis, idiomas.
                    NUNCA inventar experiencias, tecnologias, projetos ou certificacoes.

                    PASSO 3 - Listar templates de CV base
                    - Chamar listBaseResumes. Selecionar automaticamente pelo idioma da vaga
                      (vaga PT -> CV PT-BR; vaga EN -> CV EN-US). Nao perguntar se houver so um por idioma.

                    PASSO 4 - Detectar placeholders do template
                    - Chamar detectResumePlaceholders com o baseResumeId selecionado.
                    - Regra de completude: se ha N placeholders, gere N valores. Zero excecoes.

                    PASSO 5 - Perguntas minimas (so se necessario)
                    - Pergunte SOMENTE se uma informacao estiver ausente no meu CV real E na vaga,
                      e for necessaria para preencher um placeholder ou o registro.
                    - Nunca perguntar "quais tecnologias voce domina" ou "me conte seu background".

                    PASSO 6 - Gerar os valores dos placeholders
                    - Cruzar dados do CV real (passo 2) com requisitos da vaga (passo 1) e respostas (passo 5).
                    - Priorizar match com a vaga, usar linguagem ATS-friendly (keywords reais da vaga),
                      nunca inventar. Se faltar dado para placeholder obrigatorio, perguntar.

                    PASSO 7 - Criar a candidatura
                    Chamar createApplication com:
                    - vacancyName: cargo extraido
                    - organization: empresa extraida
                    - applicationDate: DATA DE HOJE (yyyy-MM-dd) — NUNCA a data da vaga
                    - vacancyLink: URL se houver, senao ""
                    - status: omitir, ou "RH" se eu ja estiver em processo
                    - recruiterName: nome do recrutador se houver, senao ""
                    - note: resumo ATS-focused (stack, senioridade, fit, gaps)
                    - interviewScheduled: false
                    - recruiterDmReminderEnabled: true se houver email de contato, senao false
                    - rhAcceptedConnection: false
                    NAO preencher nextStepDateTime.

                    PASSO 8 - Gerar o CV preenchido para a candidatura
                    - So apos createApplication retornar um UUID valido E todos os placeholders estarem gerados.
                    - Chamar generated-resumes com:
                      applicationId = UUID do passo 7
                      baseResumeId  = UUID do template do passo 3
                      placeholders  = mapa placeholder -> valor gerado no passo 6
                    - Retorna o link do Google Doc na pasta da candidatura.

                    PASSO 9 - Rascunho de email ao recrutador (se houver email de contato)
                    - Gerar email profissional e conciso (max 150 palavras), baseado no CV real.
                    - Chamar Gmail:create_draft automaticamente. Nao perguntar — gerar e informar.

                    PASSO 10 - Entrega final (me responder em PT-BR com)
                    1. Link do CV gerado (Google Doc na pasta da candidatura)
                    2. Resumo ATS: placeholders detectados + valor gerado para cada um
                    3. Confirmacao do rascunho de email (se gerado)
                    4. UUID e status da candidatura criada

                    REGRAS DE INTEGRIDADE:
                    - applicationDate = data atual SEMPRE; nunca preencher nextStepDateTime.
                    - baseResumeId (JobApplyTracker) != fileId (Google Drive). Nunca confundir.
                    - Curriculo real != template. Ler o CV real e pre-requisito para gerar valores.
                    - detectResumePlaceholders antes de generated-resumes.
                    - Nunca gerar conteudo de CV sem ter lido o CV real primeiro.
                    - Se generated-resumes falhar: manter o registro, explicar o erro,
                      e me entregar os valores de placeholder para substituicao manual.
                    """.formatted(vacancy);

            return new McpSchema.GetPromptResult(
                    "Intake de vaga (workflow autonomo completo)",
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private McpServerFeatures.SyncPromptSpecification prepareNewApplicationPrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "prepare_new_application",
                "Guides the user through logging a new job application step-by-step",
                List.of(
                        new McpSchema.PromptArgument("vacancyName",
                                "The job title or vacancy name, e.g. 'Backend Engineer'", false),
                        new McpSchema.PromptArgument("recruiterName",
                                "Recruiter's full name if known", false),
                        new McpSchema.PromptArgument("organization",
                                "Company or organisation name", false)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String vacancy   = getArg(req, "vacancyName",   "(not yet informed)");
            String recruiter = getArg(req, "recruiterName", "(not yet informed)");
            String org       = getArg(req, "organization",  "(not yet informed)");

            String text = """
                    You are helping me log a new job application in my tracker.

                    Known details so far:
                    - Vacancy: %s
                    - Recruiter: %s
                    - Organisation: %s

                    Please ask me for any missing required fields (applicationDate, rhAcceptedConnection,
                    interviewScheduled, recruiterDmReminderEnabled) and then call the `createApplication`
                    tool with the complete data. Use status "RH" for a standard LinkedIn/HR cold outreach.
                    """.formatted(vacancy, recruiter, org);

            return new McpSchema.GetPromptResult(
                    "Prepare new application: " + vacancy,
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private McpServerFeatures.SyncPromptSpecification tailorResumePrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "tailor_resume",
                "Generates a tailored resume for a specific application using Google Drive",
                List.of(
                        new McpSchema.PromptArgument("applicationId",
                                "UUID of the target job application", true)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String appId = getArg(req, "applicationId", "");

            String text = """
                    I want to tailor a resume for job application ID: %s

                    Please follow these steps:
                    1. Call `getApplication` with id="%s" to see the vacancy name and organisation.
                    2. Call `listBaseResumes` to see available resume templates.
                    3. Ask me which base resume template to use if more than one exists.
                    4. Call `copyResumeToApplication` with the applicationId and the chosen baseResumeId.
                    5. Return the Google Docs link from the response so I can start editing.
                    """.formatted(appId, appId);

            return new McpSchema.GetPromptResult(
                    "Tailor resume for application " + appId,
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private McpServerFeatures.SyncPromptSpecification summarizePipelinePrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "summarize_pipeline",
                "Produces a human-readable summary of the current job search pipeline",
                List.of()
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String text = """
                    Please summarise my current job search pipeline by following these steps:

                    1. Call `getPipelineSummary` to get aggregate statistics.
                    2. Call `listApplications` with no filters (page=0, size=10, sort=createdAt,desc)
                       to retrieve the 10 most recent applications.
                    3. Call `getOverdueApplications` to identify follow-ups that need immediate action.
                    4. Call `getGamificationProfile` to include my level and XP in the summary.

                    Produce a concise report that includes:
                    - Total applications and status breakdown
                    - Interview count vs total
                    - Overdue follow-ups requiring action
                    - Daily/weekly application rate
                    - Current gamification level, XP, and streak
                    """;

            return new McpSchema.GetPromptResult(
                    "Pipeline summary",
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private static String getArg(McpSchema.GetPromptRequest req, String key, String defaultValue) {
        if (req == null || req.arguments() == null) {
            return defaultValue;
        }
        Map<String, Object> args = req.arguments();
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        String strValue = value.toString();
        return strValue.isBlank() ? defaultValue : strValue;
    }
}
