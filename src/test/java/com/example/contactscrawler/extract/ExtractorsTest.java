package com.example.contactscrawler.extract;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.support.TestSupport;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractorsTest {

    private static final URI JSONLD_URL = URI.create("https://www.romashka.ru/contacts");
    private static final URI TEXT_URL = URI.create("https://lutik.ru/kontakty");
    private static final URI MICRO_URL = URI.create("https://vasilek.ru/");

    private final CrawlerProperties props = TestSupport.props();
    private final StructuredDataReader reader = TestSupport.reader();

    private static List<String> normalized(List<Contact> contacts) {
        return contacts.stream().map(Contact::normalizedValue).toList();
    }

    @Nested
    class Phones {
        private final PhoneExtractor extractor = new PhoneExtractor(reader, props);

        @Test
        void jsonLdTelAndTextAreNormalizedToE164AndDeduplicated() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            List<Contact> phones = extractor.extract(doc, JSONLD_URL);
            assertThat(normalized(phones)).containsExactlyInAnyOrder("+74951234567", "+78005553535");
            assertThat(phones).allSatisfy(c -> {
                assertThat(c.type()).isEqualTo(ContactType.PHONE);
                assertThat(c.domain()).isEqualTo("romashka.ru");
                assertThat(c.sourceUrl()).isEqualTo(JSONLD_URL.toString());
            });
        }

        @Test
        void requisitesDatesPricesAreNotPhones() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            assertThat(normalized(extractor.extract(doc, JSONLD_URL)))
                    .noneMatch(n -> n.contains("7701234567") || n.contains("1027700132195") || n.contains("770101001"));
        }

        @Test
        void textPhones() {
            Document doc = TestSupport.fixture("contacts-text.html", TEXT_URL.toString());
            assertThat(normalized(extractor.extract(doc, TEXT_URL)))
                    .containsExactlyInAnyOrder("+74957654321", "+79123456789");
        }

        @Test
        void microdataPhone() {
            Document doc = TestSupport.fixture("microdata.html", MICRO_URL.toString());
            assertThat(normalized(extractor.extract(doc, MICRO_URL))).contains("+73432223344");
        }

        @Test
        void invalidNumbersIgnored() {
            Document doc = TestSupport.html("<a href='tel:123'>x</a><p>ИНН 7707083893</p><p>+7 000 000-00-00</p>");
            assertThat(extractor.extract(doc, TestSupport.page())).isEmpty();
        }

        @Test
        void requisiteDigitsAreCollected() {
            assertThat(PhoneExtractor.requisiteDigits("ИНН: 7707083893 КПП 773601001"))
                    .contains("7707083893", "773601001");
        }
    }

    @Nested
    class Emails {
        private final EmailExtractor extractor = new EmailExtractor(reader, props);

        @Test
        void jsonLdAndMailtoWithoutSubject() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            List<Contact> emails = extractor.extract(doc, JSONLD_URL);
            assertThat(normalized(emails)).containsExactlyInAnyOrder("info@romashka.ru", "sales@romashka.ru");
            assertThat(emails).extracting(Contact::value).contains("Sales@Romashka.ru");
        }

        @Test
        void obfuscatedAndExcluded() {
            Document doc = TestSupport.fixture("contacts-text.html", TEXT_URL.toString());
            assertThat(normalized(extractor.extract(doc, TEXT_URL)))
                    .containsExactlyInAnyOrder("office@lutik.ru", "buh@lutik.ru", "support@lutik.ru");
        }

        @Test
        void publicDomainKeptOnlyWithOrganizationSigns() { // Q-1
            String body = "<p>Пишите ivan@gmail.com</p>";
            assertThat(extractor.extract(TestSupport.html(body), TestSupport.page())).isEmpty();
            Document withOrg = TestSupport.html(body);
            withOrg.head().append("<meta property='og:site_name' content='Ромашка'>");
            assertThat(normalized(extractor.extract(withOrg, TestSupport.page()))).containsExactly("ivan@gmail.com");
        }

        @Test
        void exclusionPatterns() {
            assertThat(extractor.isExcluded("noreply@a.ru")).isTrue();
            assertThat(extractor.isExcluded("no-reply@a.ru")).isTrue();
            assertThat(extractor.isExcluded("test@a.ru")).isTrue();
            assertThat(extractor.isExcluded("a@example.com")).isTrue();
            assertThat(extractor.isExcluded("logo@2x.png")).isTrue();
            assertThat(extractor.isExcluded("x@o1.ingest.sentry.io")).isTrue();
            assertThat(extractor.isExcluded("info@a.ru")).isFalse();
        }

        @Test
        void deobfuscation() {
            assertThat(EmailExtractor.deobfuscate("a [at] b [dot] ru")).isEqualTo("a@b.ru");
            assertThat(EmailExtractor.deobfuscate("a(at)b.ru")).isEqualTo("a@b.ru");
            assertThat(EmailExtractor.deobfuscate("a @ b . ru")).isEqualTo("a@b.ru");
            assertThat(EmailExtractor.deobfuscate("a {собака} b (точка) ru")).isEqualTo("a@b.ru");
        }

        @Test
        void microdataEmail() {
            Document doc = TestSupport.fixture("microdata.html", MICRO_URL.toString());
            assertThat(normalized(extractor.extract(doc, MICRO_URL))).containsExactly("hello@vasilek.ru");
        }
    }

    @Nested
    class Addresses {
        private final AddressExtractor extractor = new AddressExtractor(reader);

        @Test
        void jsonLdPostalAddress() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            List<Contact> addresses = extractor.extract(doc, JSONLD_URL);
            assertThat(addresses).extracting(Contact::value).containsExactly("123456, Москва, ул. Ленина, д. 5");
            assertThat(addresses).extracting(Contact::normalizedValue).containsExactly("123456, москва, ул. ленина, д. 5");
        }

        @Test
        void heuristicPicksInnermostBlock() {
            Document doc = TestSupport.fixture("contacts-text.html", TEXT_URL.toString());
            assertThat(extractor.extract(doc, TEXT_URL)).extracting(Contact::value)
                    .containsExactly("Наш адрес: 190000, г. Санкт-Петербург, наб. реки Мойки, д. 12, офис 3");
        }

        @Test
        void microdataAndAddressTag() {
            Document doc = TestSupport.fixture("microdata.html", MICRO_URL.toString());
            assertThat(extractor.extract(doc, MICRO_URL)).extracting(Contact::value)
                    .containsExactly("620000, Екатеринбург, пр-т Ленина, 1", "620000, г. Екатеринбург, пр-т Ленина, 1");
        }

        @Test
        void noAddressWithSingleFeature() {
            assertThat(extractor.extract(TestSupport.html("<p>Дом д. 5 красивый</p>"), TestSupport.page())).isEmpty();
            assertThat(AddressExtractor.featureCount("г. Москва, ул. Тверская")).isEqualTo(2);
            assertThat(AddressExtractor.normalize("  Г. Москва,   ул. Тверская.  ")).isEqualTo("г. москва, ул. тверская");
        }
    }

    @Nested
    class Socials {
        private final SocialLinkExtractor extractor = new SocialLinkExtractor(props);

        @Test
        void normalizedHandlesWithoutParams() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            List<Contact> socials = extractor.extract(doc, JSONLD_URL);
            assertThat(normalized(socials)).containsExactly("https://t.me/romashka", "https://vk.com/romashka_official");
            assertThat(socials).extracting(Contact::subtype).containsExactly("telegram", "vk");
        }

        @Test
        void whatsappYoutubeOk() {
            Document doc = TestSupport.fixture("microdata.html", MICRO_URL.toString());
            assertThat(normalized(extractor.extract(doc, MICRO_URL))).containsExactly(
                    "https://wa.me/79123456789", "https://youtube.com/channel/UC123abc", "https://ok.ru/group/5555");
        }

        @Test
        void invalidAndForeign() {
            assertThat(extractor.normalize("not a url ::")).isEmpty();
            assertThat(extractor.normalize("https://t.me/")).isEmpty();
            assertThat(extractor.normalize("https://api.whatsapp.com/send")).isEmpty();
            assertThat(extractor.normalize("https://facebook.com/x")).isEmpty();
        }
    }

    @Nested
    class OrgNames {
        private final OrgNameExtractor extractor = new OrgNameExtractor(reader);

        @Test
        void priority() {
            assertThat(extractor.extract(TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString())))
                    .contains("ООО «Ромашка»");
            assertThat(extractor.extract(TestSupport.fixture("microdata.html", MICRO_URL.toString())))
                    .contains("АО Василёк");
            assertThat(extractor.extract(TestSupport.fixture("contacts-text.html", TEXT_URL.toString())))
                    .contains("ООО Лютик");
            Document og = TestSupport.html("");
            og.head().append("<meta property='og:site_name' content='Сайт Ромашки'>");
            assertThat(extractor.extract(og)).contains("Сайт Ромашки");
            assertThat(extractor.extract(org.jsoup.Jsoup.parse("<html><body></body></html>"))).isEmpty();
        }
    }

    @Nested
    class Hours {
        private final OpeningHoursExtractor extractor = new OpeningHoursExtractor(reader);

        @Test
        void jsonLdAndMicrodata() {
            assertThat(normalized(extractor.extract(TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString()), JSONLD_URL)))
                    .containsExactly("mo-fr 09:00-18:00", "saturday 10:00-15:00");
            assertThat(normalized(extractor.extract(TestSupport.fixture("microdata.html", MICRO_URL.toString()), MICRO_URL)))
                    .containsExactly("mo-su 10:00-22:00");
        }
    }

    @Nested
    class Service {
        private final ContactExtractionService service = TestSupport.extraction(props);

        @Test
        void aggregatesDeduplicatesAddsWebsiteAndName() {
            Document doc = TestSupport.fixture("contacts-jsonld.html", JSONLD_URL.toString());
            ExtractionResult result = service.extract(doc, JSONLD_URL);
            assertThat(result.organizationName()).contains("ООО «Ромашка»");
            assertThat(result.contacts()).extracting(Contact::type)
                    .contains(ContactType.PHONE, ContactType.EMAIL, ContactType.ADDRESS, ContactType.SOCIAL,
                            ContactType.HOURS, ContactType.WEBSITE);
            assertThat(result.contacts()).extracting(Contact::key).doesNotHaveDuplicates();
            assertThat(result.contacts()).filteredOn(c -> c.type() == ContactType.WEBSITE)
                    .extracting(Contact::normalizedValue).containsExactly("https://www.romashka.ru");
        }

        @Test
        void failingExtractorDoesNotBreakOthers() {
            ContactExtractor failing = (d, u) -> {
                throw new IllegalStateException("boom");
            };
            ContactExtractionService s = new ContactExtractionService(
                    List.of(failing, new EmailExtractor(reader, props)), new OrgNameExtractor(reader));
            assertThat(s.extract(TestSupport.html("<a href='mailto:a@b.ru'>m</a>"), TestSupport.page()).contacts())
                    .extracting(Contact::type).containsExactly(ContactType.EMAIL, ContactType.WEBSITE);
        }

        @Test
        void emptyPageHasNoWebsite() {
            assertThat(service.extract(TestSupport.html("<p>nothing</p>"), TestSupport.page()).contacts()).isEmpty();
        }
    }
}
