package io.webcontify.shared.util;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.function.Predicate;

@Component
public class SlugGenerator {

    public String generate(String name) {
        String slug = name.trim().toLowerCase();
        slug = slug.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                   .replace("ß", "ss");
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD)
                         .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        slug = slug.replaceAll("[^a-z0-9\\s-]", "");
        slug = slug.replaceAll("\\s+", "-");
        slug = slug.replaceAll("-{2,}", "-");
        slug = slug.replaceAll("^-|-$", "");
        return slug;
    }

    public String generateUnique(String name, Predicate<String> existsCheck) {
        String base = generate(name);
        if (!existsCheck.test(base)) {
            return base;
        }
        int suffix = 2;
        while (existsCheck.test(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }
}
