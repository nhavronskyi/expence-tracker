package pl.havronskyi.finance.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The definitions are part of the contract with the model - they go verbatim into the prompt.
 * Without them the model mixes up Fridge/Restaurants/Delivery and Payments/Apartments,
 * because the category names alone are ambiguous.
 */
public enum Category {

    APARTMENTS("Apartments", "Rent, housing fees, utilities, building maintenance, mortgage payments."),
    PAYMENTS("Payments", "Recurring non-housing bills: mobile, internet, insurance, software subscriptions, bank fees."),
    TRANSPORT("Transport", "Public transit, taxi, ride-hailing, fuel, parking, tolls, car service."),
    CLOTHES("Clothes", "Clothing, footwear, accessories."),
    ELECTRONICS("Electronics", "Computers, phones, peripherals, components, consumer electronics."),
    MEBLES("Mebles", "Furniture and home furnishing (IKEA-type purchases)."),
    FRIDGE("Fridge", "Groceries bought to take home: supermarkets, food shops, markets. NOT eating out."),
    DELIVERY("Delivery", "Food ordered for delivery (Glovo, Pyszne, Uber Eats) and parcel courier fees."),
    HOBBY("Hobby", "Games, books, music, streaming for entertainment, crafts, collections."),
    PRESENTS("Presents", "Gifts bought for other people, flowers, celebrations."),
    RESTAURANTS("Restaurants", "Eating or drinking on site: restaurants, cafes, bars, canteens. NOT delivery."),
    TRAVELING("Traveling", "Flights, trains between cities, hotels, travel insurance, luggage."),
    TOOLS("Tools", "Hardware, DIY, workshop tools, building materials."),
    SPORT("Sport", "Gym, sports club, sports gear, sports classes."),
    HEALTH("Health", "Doctors, dentists, pharmacy, medical tests, therapy."),
    INVESTMENTS("Investments", "Brokerage funding, securities, retirement products, crypto purchases."),
    TAX("Tax", "Taxes and public dues: ZUS, US, VAT, PIT. Mostly on the business account.");

    private final String label;
    private final String definition;

    Category(String label, String definition) {
        this.label = label;
        this.definition = definition;
    }

    /**
     * Block inserted into the prompt.
     */
    public static String promptCatalog() {
        return Arrays.stream(values())
                .map(c -> "- " + c.name() + " (" + c.label + "): " + c.definition)
                .collect(Collectors.joining("\n"));
    }

    public String label() {
        return label;
    }

    public String definition() {
        return definition;
    }
}
