package com.jobtracker.service;

import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class GptAuthorizationPageRenderer {

    public String render(GptAuthorizationRequest request, String errorMessage) {
        String scopes = request.scope() == null ? "" : request.scope();
        String errorBlock = errorMessage == null || errorMessage.isBlank()
                ? ""
                : "<p style=\"color:#b91c1c;margin-bottom:16px;\">" + HtmlUtils.htmlEscape(errorMessage) + "</p>";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Authorize GPT Action</title>
                </head>
                <body style="font-family: Arial, sans-serif; background: #f8fafc; color: #0f172a; margin: 0; padding: 32px;">
                    <main style="max-width: 520px; margin: 0 auto; background: white; border-radius: 12px; padding: 24px; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);">
                        <h1 style="margin-top: 0;">Authorize GPT Action</h1>
                        <p>Sign in with your JobApplyTracker account and approve the requested scopes.</p>
                        %s
                        <p><strong>Client:</strong> %s</p>
                        <p><strong>Scopes:</strong> %s</p>
                        <form method="post" action="/oauth2/authorize">
                            <input type="hidden" name="response_type" value="%s" />
                            <input type="hidden" name="client_id" value="%s" />
                            <input type="hidden" name="redirect_uri" value="%s" />
                            <input type="hidden" name="scope" value="%s" />
                            <input type="hidden" name="state" value="%s" />
                            <input type="hidden" name="code_challenge" value="%s" />
                            <input type="hidden" name="code_challenge_method" value="%s" />
                            <label for="email" style="display:block; margin-bottom: 12px;">Email</label>
                            <input id="email" style="width:100%%; padding:8px; margin-top:4px; margin-bottom:12px;" type="email" name="email" required />
                            <label for="password" style="display:block; margin-bottom: 12px;">Password</label>
                            <input id="password" style="width:100%%; padding:8px; margin-top:4px; margin-bottom:12px;" type="password" name="password" required />
                            <label style="display:flex; gap:8px; align-items:center; margin-bottom: 16px;"><input type="checkbox" name="approve" value="true" required />Approve access for this GPT Action</label>
                            <button style="background:#2563eb; color:white; border:none; padding:10px 16px; border-radius:8px; cursor:pointer;" type="submit">Authorize</button>
                        </form>
                    </main>
                </body>
                </html>
                """.formatted(
                errorBlock,
                escape(request.client_id()),
                escape(scopes.isBlank() ? "default configured scopes" : scopes),
                escape(request.response_type()),
                escape(request.client_id()),
                escape(request.redirect_uri()),
                escape(scopes),
                escape(request.state()),
                escape(request.code_challenge()),
                escape(request.code_challenge_method())
        );
    }

    public MediaType mediaType() {
        return MediaType.TEXT_HTML;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
