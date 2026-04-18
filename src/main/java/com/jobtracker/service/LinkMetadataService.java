package com.jobtracker.service;

import com.jobtracker.dto.application.LinkMetadataResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Service
public class LinkMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(LinkMetadataService.class);
    private static final int TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    public LinkMetadataResponse extractMetadata(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            String title = extractTitle(doc);
            String description = extractDescription(doc);
            String image = extractImage(doc);
            String domain = extractDomain(url);

            return new LinkMetadataResponse(title, description, image, domain);
        } catch (IOException e) {
            logger.warn("Failed to fetch metadata for URL: {}", url, e);
            return new LinkMetadataResponse(null, null, null, extractDomain(url));
        }
    }

    private String extractTitle(Document doc) {
        // Try Open Graph title first
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null && !ogTitle.attr("content").isEmpty()) {
            return ogTitle.attr("content");
        }

        // Fall back to Twitter card title
        Element twitterTitle = doc.selectFirst("meta[name=twitter:title]");
        if (twitterTitle != null && !twitterTitle.attr("content").isEmpty()) {
            return twitterTitle.attr("content");
        }

        // Fall back to page title
        Element titleTag = doc.selectFirst("title");
        if (titleTag != null && !titleTag.text().isEmpty()) {
            return titleTag.text();
        }

        // Fall back to h1
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isEmpty()) {
            return h1.text();
        }

        return null;
    }

    private String extractDescription(Document doc) {
        // Try Open Graph description first
        Element ogDescription = doc.selectFirst("meta[property=og:description]");
        if (ogDescription != null && !ogDescription.attr("content").isEmpty()) {
            return ogDescription.attr("content");
        }

        // Fall back to Twitter card description
        Element twitterDescription = doc.selectFirst("meta[name=twitter:description]");
        if (twitterDescription != null && !twitterDescription.attr("content").isEmpty()) {
            return twitterDescription.attr("content");
        }

        // Fall back to meta description
        Element metaDescription = doc.selectFirst("meta[name=description]");
        if (metaDescription != null && !metaDescription.attr("content").isEmpty()) {
            return metaDescription.attr("content");
        }

        return null;
    }

    private String extractImage(Document doc) {
        // Try Open Graph image first
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null && !ogImage.attr("content").isEmpty()) {
            return ogImage.attr("content");
        }

        // Fall back to Twitter card image
        Element twitterImage = doc.selectFirst("meta[name=twitter:image]");
        if (twitterImage != null && !twitterImage.attr("content").isEmpty()) {
            return twitterImage.attr("content");
        }

        // Fall back to any image tag src
        Element img = doc.selectFirst("img");
        if (img != null && !img.attr("src").isEmpty()) {
            return img.attr("src");
        }

        return null;
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            if (domain != null) {
                // Remove www. prefix if present
                if (domain.startsWith("www.")) {
                    domain = domain.substring(4);
                }
                return domain;
            }
        } catch (URISyntaxException e) {
            logger.warn("Failed to parse domain from URL: {}", url, e);
        }
        return url;
    }
}
