package pl.havronskyi.finance.workspace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.havronskyi.finance.domain.Category;
import pl.havronskyi.finance.domain.ImportBatch;
import pl.havronskyi.finance.domain.Workspace;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.CategoryRepository;
import pl.havronskyi.finance.repo.ImportBatchRepository;
import pl.havronskyi.finance.repo.MerchantRuleRepository;
import pl.havronskyi.finance.repo.RawTransactionRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;
import pl.havronskyi.finance.repo.WorkspaceRepository;

import java.util.Comparator;
import java.util.List;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final MerchantRuleRepository rules;
    private final ImportBatchRepository batches;
    private final RawTransactionRepository raws;
    private final TxnRepository txns;
    private final ReviewItemRepository reviews;

    public WorkspaceService(WorkspaceRepository workspaces, AccountRepository accounts,
                            CategoryRepository categories, MerchantRuleRepository rules,
                            ImportBatchRepository batches, RawTransactionRepository raws,
                            TxnRepository txns, ReviewItemRepository reviews) {
        this.workspaces = workspaces;
        this.accounts = accounts;
        this.categories = categories;
        this.rules = rules;
        this.batches = batches;
        this.raws = raws;
        this.txns = txns;
        this.reviews = reviews;
    }

    public List<Workspace> list() {
        return workspaces.findAll().stream()
                .sorted(Comparator.comparing(Workspace::getCreatedAt))
                .toList();
    }

    private record DefaultCategory(String code, String label, String definition) {
    }

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("APARTMENTS", "Apartments",
                    "Rent, housing fees, utilities, building maintenance, mortgage payments."),
            new DefaultCategory("PAYMENTS", "Payments",
                    "Recurring non-housing bills: mobile, internet, insurance, software subscriptions, bank fees."),
            new DefaultCategory("TRANSPORT", "Transport",
                    "Public transit, taxi, ride-hailing, fuel, parking, tolls, car service."),
            new DefaultCategory("CLOTHES", "Clothes", "Clothing, footwear, accessories."),
            new DefaultCategory("ELECTRONICS", "Electronics",
                    "Computers, phones, peripherals, components, consumer electronics."),
            new DefaultCategory("MEBLES", "Mebles", "Furniture and home furnishing (IKEA-type purchases)."),
            new DefaultCategory("FRIDGE", "Fridge",
                    "Groceries bought to take home: supermarkets, food shops, markets. NOT eating out."),
            new DefaultCategory("DELIVERY", "Delivery",
                    "Food ordered for delivery (Glovo, Pyszne, Uber Eats) and parcel courier fees."),
            new DefaultCategory("HOBBY", "Hobby",
                    "Games, books, music, streaming for entertainment, crafts, collections."),
            new DefaultCategory("PRESENTS", "Presents", "Gifts bought for other people, flowers, celebrations."),
            new DefaultCategory("RESTAURANTS", "Restaurants",
                    "Eating or drinking on site: restaurants, cafes, bars, canteens. NOT delivery."),
            new DefaultCategory("TRAVELING", "Traveling",
                    "Flights, trains between cities, hotels, travel insurance, luggage."),
            new DefaultCategory("TOOLS", "Tools", "Hardware, Professional."),
            new DefaultCategory("SPORT", "Sport", "Gym, sports club, sports gear, sports classes."),
            new DefaultCategory("HEALTH", "Health", "Doctors, dentists, pharmacy, medical tests, therapy."),
            new DefaultCategory("INVESTMENTS", "Investments",
                    "Brokerage funding, securities, retirement products, crypto purchases."),
            new DefaultCategory("SAVINGS", "Savings",
                    "Money set aside into savings accounts or an emergency fund."));

    /**
     * Every new workspace starts with this starter set so there's something for the rule
     * engine/LLM to categorize against immediately - matching the set the app originally
     * shipped with (V3__category_table.sql), swapping TAX for SAVINGS.
     */
    @Transactional
    public Workspace create(String name) {
        Workspace w = new Workspace();
        w.setName(name);
        w = workspaces.save(w);

        for (DefaultCategory dc : DEFAULT_CATEGORIES) {
            Category c = new Category();
            c.setWorkspaceId(w.getId());
            c.setCode(dc.code());
            c.setLabel(dc.label());
            c.setDefinition(dc.definition());
            c.setActive(true);
            categories.save(c);
        }

        return w;
    }

    /**
     * Deletes the workspace and everything inside it. Order matters - each step below must
     * run before the table it references is emptied, same dependency order already proven
     * out in ImportService.clearTransactionData: review_item -> txn -> raw_transaction (via
     * its batch ids) -> import_batch, then the entities with no further children -
     * account, category, merchant_rule - and finally the workspace row itself.
     */
    @Transactional
    public void delete(Long workspaceId) {
        if (!workspaces.existsById(workspaceId)) {
            throw new IllegalArgumentException("Brak workspace " + workspaceId);
        }
        List<Long> batchIds = batches.findAllByWorkspaceId(workspaceId).stream()
                .map(ImportBatch::getId)
                .toList();

        reviews.deleteAllByWorkspaceId(workspaceId);
        txns.deleteAllByWorkspaceId(workspaceId);
        raws.deleteAllByBatchIdIn(batchIds);
        batches.deleteAllByWorkspaceId(workspaceId);
        accounts.deleteAllByWorkspaceId(workspaceId);
        categories.deleteAllByWorkspaceId(workspaceId);
        rules.deleteAllByWorkspaceId(workspaceId);
        workspaces.deleteById(workspaceId);
    }
}
