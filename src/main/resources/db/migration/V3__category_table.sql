-- Categories move from a fixed Java enum to real data so they can be added from the app.
-- txn.category / merchant_rule.category already store the plain code string (e.g. 'FRIDGE'),
-- so no change is needed there - this table is purely additive.
CREATE TABLE category
(
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    definition TEXT         NOT NULL DEFAULT '',
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO category (code, label, definition)
VALUES ('APARTMENTS', 'Apartments', 'Rent, housing fees, utilities, building maintenance, mortgage payments.'),
       ('PAYMENTS', 'Payments', 'Recurring non-housing bills: mobile, internet, insurance, software subscriptions, bank fees.'),
       ('TRANSPORT', 'Transport', 'Public transit, taxi, ride-hailing, fuel, parking, tolls, car service.'),
       ('CLOTHES', 'Clothes', 'Clothing, footwear, accessories.'),
       ('ELECTRONICS', 'Electronics', 'Computers, phones, peripherals, components, consumer electronics.'),
       ('MEBLES', 'Mebles', 'Furniture and home furnishing (IKEA-type purchases).'),
       ('FRIDGE', 'Fridge', 'Groceries bought to take home: supermarkets, food shops, markets. NOT eating out.'),
       ('DELIVERY', 'Delivery', 'Food ordered for delivery (Glovo, Pyszne, Uber Eats) and parcel courier fees.'),
       ('HOBBY', 'Hobby', 'Games, books, music, streaming for entertainment, crafts, collections.'),
       ('PRESENTS', 'Presents', 'Gifts bought for other people, flowers, celebrations.'),
       ('RESTAURANTS', 'Restaurants', 'Eating or drinking on site: restaurants, cafes, bars, canteens. NOT delivery.'),
       ('TRAVELING', 'Traveling', 'Flights, trains between cities, hotels, travel insurance, luggage.'),
       ('TOOLS', 'Tools', 'Hardware, DIY, workshop tools, building materials.'),
       ('SPORT', 'Sport', 'Gym, sports club, sports gear, sports classes.'),
       ('HEALTH', 'Health', 'Doctors, dentists, pharmacy, medical tests, therapy.'),
       ('INVESTMENTS', 'Investments', 'Brokerage funding, securities, retirement products, crypto purchases.'),
       ('TAX', 'Tax', 'Taxes and public dues: ZUS, US, VAT, PIT. Mostly on the business account.');
