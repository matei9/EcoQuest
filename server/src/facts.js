// Fun facts shown on the home screen + the daily trivia built from them.
// Numbers are rounded, widely cited values (sources in comments). Difficulty: 1 easy, 2 medium, 3 hard.

const F = (id, difficulty, fact, question, options, correct) => ({ id, difficulty, fact, question, options, correct });
const L = (ro, en) => ({ ro, en });
const O = (...pairs) => pairs.map(([ro, en]) => L(ro, en));

export const facts = [
  // ---------- easy ----------
  F('ice-per-tonne', 1,
    L('Fiecare tonă de CO₂ emisă topește aproximativ 3 m² de gheață arctică de vară (Notz & Stroeve).', 'Every tonne of CO₂ emitted melts about 3 m² of Arctic summer sea ice (Notz & Stroeve).'),
    L('Câtă gheață arctică de vară se pierde pentru fiecare tonă de CO₂ emisă?', 'How much Arctic summer sea ice is lost per tonne of CO₂ emitted?'),
    O(['Aproximativ 3 cm²', 'About 3 cm²'], ['Aproximativ 3 m²', 'About 3 m²'], ['Aproximativ 300 m²', 'About 300 m²'], ['Aproximativ 3 km²', 'About 3 km²']), 1),
  F('sgr-deposit', 1,
    L('În sistemul SGR din România, garanția pentru fiecare ambalaj returnabil este de 0,50 lei.', 'In Romania\'s SGR deposit system, the deposit for each returnable package is 0.50 lei.'),
    L('Cât este garanția SGR pentru un ambalaj?', 'How much is the SGR deposit per package?'),
    O(['0,10 lei', '0.10 lei'], ['0,50 lei', '0.50 lei'], ['1 leu', '1 leu'], ['2 lei', '2 lei']), 1),
  F('bears-seals', 1,
    L('Urșii polari folosesc gheața marină ca platformă pentru a vâna foci — fără gheață, rămân fără hrană.', 'Polar bears use sea ice as a platform to hunt seals — without ice, they lose their food.'),
    L('Pentru ce au nevoie urșii polari, în principal, de gheața marină?', 'What do polar bears mainly need sea ice for?'),
    O(['Pentru a vâna foci', 'To hunt seals'], ['Pentru a bea apă', 'To drink water'], ['Pentru a se ascunde de oameni', 'To hide from people'], ['Pentru a hiberna toată vara', 'To hibernate all summer']), 0),
  F('aluminium-energy', 1,
    L('Reciclarea unei doze de aluminiu economisește aproximativ 95% din energia necesară producerii uneia noi din minereu.', 'Recycling an aluminium can saves about 95% of the energy needed to make a new one from ore.'),
    L('Câtă energie economisește reciclarea aluminiului față de producția din minereu?', 'How much energy does recycling aluminium save compared to making it from ore?'),
    O(['Aproximativ 10%', 'About 10%'], ['Aproximativ 40%', 'About 40%'], ['Aproximativ 95%', 'About 95%'], ['Nimic', 'None']), 2),
  F('led', 1,
    L('Becurile LED consumă cu cel puțin 75% mai puțină energie decât becurile incandescente.', 'LED bulbs use at least 75% less energy than incandescent bulbs.'),
    L('Cu cât consumă mai puțin un bec LED decât unul incandescent?', 'How much less energy does an LED bulb use than an incandescent one?'),
    O(['Cu circa 5% mai puțin', 'About 5% less'], ['Cu circa 25% mai puțin', 'About 25% less'], ['Cu cel puțin 75% mai puțin', 'At least 75% less'], ['Consumă la fel', 'The same']), 2),
  F('food-third', 1,
    L('Aproximativ o treime din hrana produsă în lume se pierde sau se risipește (FAO).', 'About one third of the food produced worldwide is lost or wasted (FAO).'),
    L('Ce parte din hrana produsă în lume se pierde sau se risipește?', 'What share of the world\'s food is lost or wasted?'),
    O(['Circa 1%', 'About 1%'], ['Circa o zecime', 'About a tenth'], ['Circa o treime', 'About a third'], ['Peste trei sferturi', 'Over three quarters']), 2),
  F('plastic-bottle', 1,
    L('O sticlă de plastic poate rămâne în natură sute de ani — de aceea o sticlă reutilizabilă contează.', 'A plastic bottle can remain in nature for hundreds of years — that\'s why a reusable bottle matters.'),
    L('Cât poate rămâne o sticlă de plastic în natură?', 'How long can a plastic bottle remain in nature?'),
    O(['Câteva săptămâni', 'A few weeks'], ['1–2 ani', '1–2 years'], ['Sute de ani', 'Hundreds of years'], ['Se descompune într-o zi', 'It breaks down in a day']), 2),
  F('tap-flow', 1,
    L('Un robinet deschis curge cu aproximativ 6–8 litri pe minut; închide-l când te speli pe dinți.', 'An open tap runs about 6–8 litres per minute; turn it off while brushing your teeth.'),
    L('Câți litri pe minut curg, aproximativ, dintr-un robinet deschis?', 'Roughly how many litres per minute flow from an open tap?'),
    O(['Sub 1 litru', 'Under 1 litre'], ['Aproximativ 6–8 litri', 'About 6–8 litres'], ['Aproximativ 50 de litri', 'About 50 litres'], ['Peste 100 de litri', 'Over 100 litres']), 1),
  F('co2-main-gas', 1,
    L('Dioxidul de carbon (CO₂) este principalul gaz cu efect de seră emis prin activitățile umane.', 'Carbon dioxide (CO₂) is the main greenhouse gas emitted by human activities.'),
    L('Care este principalul gaz cu efect de seră emis de oameni?', 'Which is the main greenhouse gas emitted by people?'),
    O(['Oxigenul', 'Oxygen'], ['Dioxidul de carbon', 'Carbon dioxide'], ['Azotul', 'Nitrogen'], ['Heliul', 'Helium']), 1),
  F('walk-zero', 1,
    L('Mersul pe jos și cu bicicleta nu produc emisii la țeava de eșapament — iar 1 km evitat cu mașina înseamnă ~0,2 kg CO₂ în minus.', 'Walking and cycling produce no tailpipe emissions — and 1 km not driven means ~0.2 kg less CO₂.'),
    L('Cât CO₂ eviți, aproximativ, pentru 1 km parcurs pe jos în loc de mașină?', 'About how much CO₂ do you avoid per 1 km walked instead of driven?'),
    O(['Aproximativ 0,2 kg', 'About 0.2 kg'], ['Aproximativ 20 kg', 'About 20 kg'], ['Aproximativ 2 g', 'About 2 g'], ['Nimic', 'Nothing']), 0),

  // ---------- medium ----------
  F('tree-22kg', 2,
    L('Un copac matur absoarbe, în medie, aproximativ 22 kg de CO₂ pe an (estimare des folosită).', 'A mature tree absorbs on average about 22 kg of CO₂ per year (a commonly used estimate).'),
    L('Cât CO₂ absoarbe, în medie, un copac matur într-un an?', 'About how much CO₂ does a mature tree absorb per year?'),
    O(['Aproximativ 22 g', 'About 22 g'], ['Aproximativ 22 kg', 'About 22 kg'], ['Aproximativ 2,2 tone', 'About 2.2 tonnes'], ['Aproximativ 220 tone', 'About 220 tonnes']), 1),
  F('car-per-km', 2,
    L('O mașină obișnuită pe benzină emite aproximativ 120–200 g de CO₂ pe kilometru.', 'A typical petrol car emits about 120–200 g of CO₂ per kilometre.'),
    L('Cât CO₂ emite pe kilometru o mașină obișnuită pe benzină?', 'How much CO₂ does a typical petrol car emit per kilometre?'),
    O(['2–5 g', '2–5 g'], ['120–200 g', '120–200 g'], ['1–2 kg', '1–2 kg'], ['10 kg', '10 kg']), 1),
  F('petrol-litre', 2,
    L('Arderea unui litru de benzină produce aproximativ 2,3 kg de CO₂.', 'Burning one litre of petrol produces about 2.3 kg of CO₂.'),
    L('Cât CO₂ rezultă din arderea unui litru de benzină?', 'How much CO₂ comes from burning one litre of petrol?'),
    O(['Aproximativ 0,2 kg', 'About 0.2 kg'], ['Aproximativ 2,3 kg', 'About 2.3 kg'], ['Aproximativ 23 kg', 'About 23 kg'], ['Aproximativ 230 kg', 'About 230 kg']), 1),
  F('food-waste-ghg', 2,
    L('Risipa alimentară produce aproximativ 8–10% din emisiile globale de gaze cu efect de seră (UNEP).', 'Food waste causes about 8–10% of global greenhouse gas emissions (UNEP).'),
    L('Ce procent din emisiile globale provin din risipa alimentară?', 'What share of global emissions comes from food waste?'),
    O(['Sub 1%', 'Under 1%'], ['Aproximativ 8–10%', 'About 8–10%'], ['Aproximativ 40%', 'About 40%'], ['Peste 70%', 'Over 70%']), 1),
  F('beef', 2,
    L('Carnea de vită are cea mai mare amprentă de carbon per kilogram dintre alimentele comune — de zeci de ori mai mult decât leguminoasele.', 'Beef has the highest carbon footprint per kilogram of common foods — dozens of times more than legumes.'),
    L('Care aliment are cea mai mare amprentă de carbon per kilogram?', 'Which food has the highest carbon footprint per kilogram?'),
    O(['Lintea', 'Lentils'], ['Orezul', 'Rice'], ['Carnea de pui', 'Chicken'], ['Carnea de vită', 'Beef']), 3),
  F('standby', 2,
    L('Aparatele lăsate în standby pot consuma 5–10% din electricitatea unei locuințe.', 'Devices left on standby can use 5–10% of a home\'s electricity.'),
    L('Cât din electricitatea casei poate merge pe aparatele în standby?', 'How much of a home\'s electricity can standby devices use?'),
    O(['Aproape nimic', 'Almost nothing'], ['5–10%', '5–10%'], ['Aproximativ 50%', 'About 50%'], ['Peste 80%', 'Over 80%']), 1),
  F('wash-30', 2,
    L('Spălatul rufelor la 30°C în loc de temperaturi mai mari poate reduce consumul de energie al mașinii cu până la ~40%.', 'Washing clothes at 30°C instead of higher temperatures can cut the machine\'s energy use by up to ~40%.'),
    L('Cu cât poate scădea energia consumată dacă speli la 30°C?', 'By how much can energy use drop if you wash at 30°C?'),
    O(['Cu până la ~40%', 'By up to ~40%'], ['Cu ~1%', 'By ~1%'], ['Crește consumul', 'It goes up'], ['Cu 100%', 'By 100%']), 0),
  F('glass-endless', 2,
    L('Sticla poate fi reciclată la nesfârșit fără să își piardă calitatea.', 'Glass can be recycled endlessly without losing quality.'),
    L('De câte ori poate fi reciclată sticla fără pierderi de calitate?', 'How many times can glass be recycled without losing quality?'),
    O(['O singură dată', 'Only once'], ['De 2–3 ori', '2–3 times'], ['De aproximativ 10 ori', 'About 10 times'], ['La nesfârșit', 'Endlessly']), 3),
  F('train-lowest', 2,
    L('Per pasager-kilometru, trenul emite de câteva ori mai puțin CO₂ decât o mașină cu un singur ocupant.', 'Per passenger-kilometre, the train emits several times less CO₂ than a car with a single occupant.'),
    L('Care variantă emite cel mai puțin CO₂ per pasager-kilometru?', 'Which option emits the least CO₂ per passenger-kilometre?'),
    O(['Mașina cu un singur ocupant', 'A car with one occupant'], ['Zborul scurt', 'A short flight'], ['Trenul', 'The train'], ['Taxiul', 'A taxi']), 2),
  F('tshirt-water', 2,
    L('Pentru un singur tricou din bumbac sunt necesari aproximativ 2.700 de litri de apă (WWF).', 'About 2,700 litres of water are needed to make one cotton T-shirt (WWF).'),
    L('Câtă apă este necesară, aproximativ, pentru un tricou din bumbac?', 'About how much water does one cotton T-shirt take?'),
    O(['Aproximativ 2,7 litri', 'About 2.7 litres'], ['Aproximativ 27 de litri', 'About 27 litres'], ['Aproximativ 2.700 de litri', 'About 2,700 litres'], ['Aproximativ 270.000 de litri', 'About 270,000 litres']), 2),

  // ---------- hard ----------
  F('notz-2016', 3,
    L('Studiul Notz & Stroeve care leagă CO₂ de topirea gheții arctice a fost publicat în revista Science, în 2016.', 'The Notz & Stroeve study linking CO₂ to Arctic ice loss was published in Science in 2016.'),
    L('În ce an și unde a apărut studiul Notz & Stroeve despre gheața arctică?', 'When and where was the Notz & Stroeve Arctic ice study published?'),
    O(['Nature, 1998', 'Nature, 1998'], ['Science, 2016', 'Science, 2016'], ['The Lancet, 2020', 'The Lancet, 2020'], ['National Geographic, 2005', 'National Geographic, 2005']), 1),
  F('ice-decline', 3,
    L('Minimul de gheață arctică din septembrie scade cu aproximativ 13% pe deceniu față de media 1981–2010 (NASA).', 'The September Arctic sea ice minimum is shrinking by about 13% per decade relative to the 1981–2010 average (NASA).'),
    L('Cu cât scade, pe deceniu, minimul de gheață arctică din septembrie?', 'How fast is the September Arctic sea ice minimum shrinking per decade?'),
    O(['Aproximativ 1%', 'About 1%'], ['Aproximativ 13%', 'About 13%'], ['Aproximativ 50%', 'About 50%'], ['Nu scade', 'It isn\'t shrinking']), 1),
  F('bear-population', 3,
    L('În lume trăiesc aproximativ 22.000–31.000 de urși polari (IUCN).', 'About 22,000–31,000 polar bears live in the world (IUCN).'),
    L('Câți urși polari trăiesc, aproximativ, în lume?', 'Roughly how many polar bears live in the world?'),
    O(['Aproximativ 500', 'About 500'], ['Aproximativ 22.000–31.000', 'About 22,000–31,000'], ['Aproximativ 1 milion', 'About 1 million'], ['Aproximativ 10 milioane', 'About 10 million']), 1),
  F('sgr-launch', 3,
    L('Sistemul de garanție-returnare (SGR) a pornit în România pe 30 noiembrie 2023.', 'Romania\'s deposit-return system (SGR) started on 30 November 2023.'),
    L('Când a pornit sistemul SGR în România?', 'When did the SGR system start in Romania?'),
    O(['Ianuarie 2015', 'January 2015'], ['Noiembrie 2023', 'November 2023'], ['Martie 2020', 'March 2020'], ['Încă nu a pornit', 'It hasn\'t started yet']), 1),
  F('per-capita', 3,
    L('Media globală a emisiilor de CO₂ din combustibili fosili este de aproximativ 4–5 tone pe persoană pe an.', 'Global average fossil CO₂ emissions are about 4–5 tonnes per person per year.'),
    L('Cât CO₂ emite, în medie, un om pe an la nivel global?', 'How much CO₂ does the average person emit per year globally?'),
    O(['Aproximativ 50 kg', 'About 50 kg'], ['Aproximativ 4–5 tone', 'About 4–5 tonnes'], ['Aproximativ 40 de tone', 'About 40 tonnes'], ['Aproximativ 400 de tone', 'About 400 tonnes']), 1),
  F('methane', 3,
    L('Pe un orizont de 20 de ani, metanul încălzește planeta de aproximativ 80 de ori mai puternic decât CO₂ (IPCC).', 'Over 20 years, methane warms the planet about 80 times more strongly than CO₂ (IPCC).'),
    L('De câte ori este metanul mai puternic decât CO₂ pe 20 de ani?', 'How many times more potent than CO₂ is methane over 20 years?'),
    O(['De 2 ori', '2 times'], ['De aproximativ 8 ori', 'About 8 times'], ['De aproximativ 80 de ori', 'About 80 times'], ['De 800 de ori', '800 times']), 2),
  F('paris', 3,
    L('Acordul de la Paris urmărește limitarea încălzirii globale mult sub 2°C, cu eforturi pentru 1,5°C.', 'The Paris Agreement aims to keep warming well below 2°C, pursuing efforts for 1.5°C.'),
    L('Ce limită de încălzire urmărește Acordul de la Paris?', 'Which warming limit does the Paris Agreement aim for?'),
    O(['Mult sub 2°C, ideal 1,5°C', 'Well below 2°C, ideally 1.5°C'], ['Maxim 5°C', 'At most 5°C'], ['Exact 3°C', 'Exactly 3°C'], ['Nu stabilește o limită', 'It sets no limit']), 0),
  F('ppm-280', 3,
    L('Concentrația de CO₂ din atmosferă era de ~280 ppm înainte de era industrială; azi depășește 420 ppm.', 'Atmospheric CO₂ was ~280 ppm before the industrial era; today it is above 420 ppm.'),
    L('Care era concentrația de CO₂ din atmosferă înainte de era industrială?', 'What was atmospheric CO₂ before the industrial era?'),
    O(['Aproximativ 28 ppm', 'About 28 ppm'], ['Aproximativ 280 ppm', 'About 280 ppm'], ['Aproximativ 420 ppm', 'About 420 ppm'], ['Aproximativ 2.800 ppm', 'About 2,800 ppm']), 1),
  F('warming-1-1', 3,
    L('Planeta s-a încălzit deja cu aproximativ 1,1°C față de perioada preindustrială (IPCC).', 'The planet has already warmed by about 1.1°C compared with pre-industrial times (IPCC).'),
    L('Cu cât s-a încălzit deja planeta față de perioada preindustrială?', 'How much has the planet already warmed since pre-industrial times?'),
    O(['Aproximativ 0,1°C', 'About 0.1°C'], ['Aproximativ 1,1°C', 'About 1.1°C'], ['Aproximativ 4°C', 'About 4°C'], ['Deloc', 'Not at all']), 1),
  F('eu-2050', 3,
    L('Legea europeană a climei obligă UE să devină neutră climatic până în 2050.', 'The European Climate Law commits the EU to climate neutrality by 2050.'),
    L('Până în ce an trebuie UE să devină neutră climatic?', 'By which year must the EU become climate neutral?'),
    O(['2030', '2030'], ['2050', '2050'], ['2075', '2075'], ['2100', '2100']), 1),
];

export const factById = new Map(facts.map((f) => [f.id, f]));

/** 3 easy, 4 medium, 3 hard — already-seen facts first, so the quiz follows what the user read. */
export const TRIVIA_TIERS = [[1, 3], [2, 4], [3, 3]];

export function selectTrivia(seen, random = Math.random) {
  const shuffle = (a) => a.map((x) => [random(), x]).sort((p, q) => p[0] - q[0]).map((p) => p[1]);
  return TRIVIA_TIERS.flatMap(([difficulty, n]) => {
    const tier = facts.filter((f) => f.difficulty === difficulty);
    return [...shuffle(tier.filter((f) => seen.has(f.id))), ...shuffle(tier.filter((f) => !seen.has(f.id)))].slice(0, n);
  });
}

/** Partial credit from 8/10 (owner rule). PLACEHOLDER values. */
export function triviaReward(score) {
  if (score >= 10) return { ep: 100, coins: 20 };
  if (score === 9) return { ep: 70, coins: 14 };
  if (score === 8) return { ep: 50, coins: 10 };
  return { ep: 0, coins: 0 };
}
