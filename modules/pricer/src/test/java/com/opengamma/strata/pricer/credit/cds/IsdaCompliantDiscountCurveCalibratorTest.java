/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.credit.cds;

import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConvention;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.ImmutableMarketDataBuilder;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveNode;
import com.opengamma.strata.market.curve.node.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.node.TermDepositCurveNode;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.product.deposit.type.ImmutableTermDepositConvention;
import com.opengamma.strata.product.deposit.type.TermDepositConvention;
import com.opengamma.strata.product.deposit.type.TermDepositConventions;
import com.opengamma.strata.product.deposit.type.TermDepositTemplate;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.product.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedIborSwapConvention;

/**
 * Test {@link IsdaCompliantDiscountCurveCalibrator}.
 */
@Test
public class IsdaCompliantDiscountCurveCalibratorTest {

  private static final DayCount ACT365 = DayCounts.ACT_365F;
  private static final DayCount ACT360 = DayCounts.ACT_360;
  private static final DayCount D30360 = DayCounts.THIRTY_U_360;
  private static final DayCount ACT_ACT = DayCounts.ACT_ACT_ISDA;

  private static final BusinessDayConvention FOLLOWING = BusinessDayConventions.FOLLOWING;
  private static final BusinessDayConvention MOD_FOLLOWING = BusinessDayConventions.MODIFIED_FOLLOWING;
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendarId CALENDAR = HolidayCalendarIds.SAT_SUN;
  private static final BusinessDayAdjustment BUS_ADJ = BusinessDayAdjustment.of(MOD_FOLLOWING, CALENDAR);
  private static final DaysAdjustment ADJ_0D = DaysAdjustment.ofBusinessDays(0, CALENDAR);
  private static final DaysAdjustment ADJ_3D = DaysAdjustment.ofBusinessDays(3, CALENDAR);

  private static final TermDepositConvention TERM_0 = ImmutableTermDepositConvention.builder()
      .businessDayAdjustment(BUS_ADJ)
      .currency(Currency.USD)
      .dayCount(ACT360)
      .name("standar_usd")
      .spotDateOffset(ADJ_0D)
      .build();
  private static final FixedRateSwapLegConvention FIXED_LEG = FixedRateSwapLegConvention.of(
      Currency.USD, D30360, Frequency.P6M, BUS_ADJ);
  private static final IborRateSwapLegConvention FLOATING_LEG = IborRateSwapLegConvention.of(IborIndices.USD_LIBOR_3M);
  private static final FixedIborSwapConvention SWAP_0 =
      ImmutableFixedIborSwapConvention.of("standar_usd", FIXED_LEG, FLOATING_LEG, ADJ_0D);
  private static final TermDepositConvention TERM_3 = ImmutableTermDepositConvention.builder()
      .businessDayAdjustment(BUS_ADJ)
      .currency(Currency.USD)
      .dayCount(ACT360)
      .name("standar_usd")
      .spotDateOffset(ADJ_3D)
      .build();
  private static final FixedIborSwapConvention SWAP_3 =
      ImmutableFixedIborSwapConvention.of("standar_usd", FIXED_LEG, FLOATING_LEG, ADJ_3D);
  private static final IsdaCompliantDiscountCurveCalibrator CALIBRATOR = IsdaCompliantDiscountCurveCalibrator.DEFAULT;

  private static final double[] RATES = new double[] {
      0.00340055550701297, 0.00636929056400781, 0.0102617798438113, 0.0135851258907251, 0.0162809551414651, 0.020583125112332,
      0.0227369218210212, 0.0251978805237614, 0.0273223815467694, 0.0310882447627048, 0.0358397743454067, 0.036047665095421,
      0.0415916567616181, 0.044066373237682, 0.046708518178509, 0.0491196954851753, 0.0529297239911766, 0.0562025436376854,
      0.0589772202773522, 0.0607471217692999};
  private static final double[] SAMPLE_TIMES = new double[] {
      0.0767123287671233, 0.167123287671233, 0.249315068493151, 0.498630136986301, 0.747945205479452, 0.997260273972603,
      1.4958904109589, 1.99452054794521, 2.5013698630137, 3.0027397260274, 3.5041095890411, 4.0027397260274, 4.5041095890411,
      5.0027397260274, 5.5041095890411, 6.0027397260274, 6.5013698630137, 7, 7.50684931506849, 8.00547945205479, 8.50684931506849,
      9.00547945205479, 9.50684931506849, 10.0054794520548, 10.5068493150685, 11.0082191780822, 11.5068493150685,
      12.0054794520548, 12.5041095890411, 13.0027397260274, 13.5095890410959, 14.0082191780822, 14.5095890410959,
      15.0109589041096, 15.5123287671233, 16.0109589041096, 16.5123287671233, 17.0109589041096, 17.5095890410959,
      18.0082191780822, 18.5068493150685, 19.013698630137, 19.5150684931507, 20.013698630137, 20.5150684931507, 21.013698630137,
      21.5150684931507, 22.013698630137, 22.5150684931507, 23.013698630137, 23.5123287671233, 24.0109589041096, 24.5178082191781,
      25.0164383561644, 25.5178082191781, 26.0164383561644, 26.5178082191781, 27.0191780821918, 27.5205479452055,
      28.0191780821918, 28.5178082191781, 29.0164383561644, 29.5150684931507, 30.013698630137};
  private static final double TOL = 1.0e-10;
  private static final double EPS = 1.0e-5;

  public void trimTest() {
    double[] zeroRates = new double[] {
        0.02464736121066336, 0.02464736121066336, 0.02464736121066336, 0.02464736121066336, 0.02464736121066336,
        0.0254821899695377, 0.027012709360807307, 0.0280465361894362, 0.029204804280939065, 0.03065455883987807,
        0.03299403689049641, 0.03532199174463788, 0.0382845734201572, 0.03986838271110048, 0.03962107761768301,
        0.04071080003825178, 0.04425486701530056, 0.0469132799835707, 0.04837517686541788, 0.04980581252668033,
        0.05144370269491186, 0.05268349729599413, 0.05336397055959266, 0.05397308696915879, 0.054527258367984804,
        0.0551895053187965, 0.05612436552456688, 0.056981569611008094, 0.05777040772736503, 0.05849874498524033,
        0.05918398623795018, 0.05978996352520596, 0.06031410776220924, 0.060803238896320895, 0.061260751875172076,
        0.061687345941315155, 0.06209030709894273, 0.06246750740041058, 0.06282322422359113, 0.06315924213856569,
        0.0634771533886919, 0.06387996886524806, 0.06447264699351102, 0.0650326346434449, 0.0655682527617952,
        0.06607559429151351, 0.0665620130938324, 0.06702379842706312, 0.06746749829294346, 0.06788959910797566,
        0.06829379678892818, 0.06870706799798204, 0.0691725839476467, 0.06961214403518971, 0.07003679902099998,
        0.07044290011788337, 0.07083583411671632, 0.07121418549097279, 0.0715787512402439, 0.07192838463342886,
        0.07226579143855355, 0.07259160197542544, 0.07290640396914115, 0.0732107460883053};
    LocalDate snapDate = LocalDate.of(2012, 4, 4);
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    List<CurveNode> types = createsNode(TERM_0, SWAP_0, mmMonths, swapYears, idValues);
    int nInstruments = RATES.length;
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < RATES.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), RATES[i]);
    }
    ImmutableMarketData quotes = builder.build();
    DayCount curveDCC = ACT365;
    // between nodes
    LocalDate valuationDate = LocalDate.of(2013, 5, 31);
    IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, valuationDate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = yc.getParameterCount();
    assertEquals(nCurvePoints, 14);
    int nSamplePoints = SAMPLE_TIMES.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = SAMPLE_TIMES[i];
      double zr = yc.getCurve().yValue(time);
      assertEquals(zeroRates[i], zr, TOL);
    }
    testJacobian(yc, snapDate, types, idValues, RATES);
    // after last node
    LocalDate valuationDateLate = LocalDate.of(2042, 6, 12);
    double zeroRateSingle = 0.09122545844959826;
    IsdaCompliantZeroRateDiscountFactors ycSingle = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, valuationDateLate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    assertEquals(ycSingle.getParameterCount(), 1);
    for (int i = 0; i < nSamplePoints; i++) {
      double time = SAMPLE_TIMES[i];
      double zr = ycSingle.getCurve().yValue(time);
      assertEquals(zeroRateSingle, zr, TOL);
    }
    testJacobian(ycSingle, snapDate, types, idValues, RATES);
  }

  public void regressionTest1() {
    // date from ISDA excel
    double[] zeroRates = new double[] {0.00344732957665484, 0.00645427070262317, 0.010390833731528, 0.0137267241507424,
        0.016406009142171, 0.0206548075787697, 0.0220059788254565, 0.0226815644487997, 0.0241475224808774, 0.0251107341245228,
        0.0263549710022889, 0.0272832610741453, 0.0294785565070328, 0.0312254350680597, 0.0340228731758456, 0.0363415444446394,
        0.0364040719835966, 0.0364576914896066, 0.0398713425199977, 0.0428078389323812, 0.0443206903065534, 0.0456582004054368,
        0.0473373527805339, 0.0488404232471453, 0.0496433764260127, 0.0503731885238783, 0.0510359350109291, 0.0516436290741354,
        0.0526405492486405, 0.0535610094687589, 0.05442700569164, 0.0552178073994544, 0.0559581527041068, 0.0566490425640605,
        0.0572429526830672, 0.0577967261153023, 0.0583198210222109, 0.0588094750567186, 0.0592712408001043, 0.0597074348516306,
        0.0601201241459759, 0.0605174325075768, 0.0608901411604128, 0.0612422922398251, 0.0618707980423834, 0.0624661234885966,
        0.0630368977571603, 0.0635787665840882, 0.064099413535239, 0.0645947156962813, 0.0650690099353217, 0.0655236050526131,
        0.0659667431709796, 0.0663851731522577, 0.0668735344788778, 0.0673405584796377, 0.0677924400667054, 0.0682275513575991,
        0.0686468089170376, 0.0690488939824011, 0.0694369182384849, 0.06981160656508, 0.0701736348572483, 0.0705236340943412};
    LocalDate spotDate = LocalDate.of(2013, 5, 31);
    int nInstruments = RATES.length;
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    List<CurveNode> types = createsNode(TERM_0, SWAP_0, mmMonths, swapYears, idValues);
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(spotDate);
    for (int i = 0; i < RATES.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), RATES[i]);
    }
    ImmutableMarketData quotes = builder.build();
    DayCount curveDCC = ACT365;
    IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, spotDate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = yc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = SAMPLE_TIMES.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = SAMPLE_TIMES[i];
      double zr = yc.getCurve().yValue(time);
      assertEquals(zeroRates[i], zr, TOL);
    }
    testJacobian(yc, spotDate, types, idValues, RATES);
  }

  private void testJacobian(
      IsdaCompliantZeroRateDiscountFactors curve,
      LocalDate snapDate,
      List<CurveNode> nodes,
      String[] idValues,
      double[] rates) {
    
    DayCount curveDcc = curve.getDayCount();
    LocalDate valuationDate = curve.getValuationDate();
    CurveName curveName = curve.getCurve().getName();
    Currency currency = curve.getCurrency();
    int nInstruments = rates.length;
    int nCurveNode = curve.getParameterCount();
    for (int i = 0; i < nInstruments; ++i) {
      double[] ratesUp = Arrays.copyOf(rates, nInstruments);
      double[] ratesDw = Arrays.copyOf(rates, nInstruments);
      ratesUp[i] += EPS;
      ratesDw[i] -= EPS;
      ImmutableMarketDataBuilder builderUp = ImmutableMarketData.builder(snapDate);
      for (int j = 0; j < nInstruments; j++) {
        builderUp.addValue(QuoteId.of(StandardId.of("OG", idValues[j])), ratesUp[j]);
      }
      ImmutableMarketData quotesUp = builderUp.build();
      ImmutableMarketDataBuilder builderDw = ImmutableMarketData.builder(snapDate);
      for (int j = 0; j < nInstruments; j++) {
        builderDw.addValue(QuoteId.of(StandardId.of("OG", idValues[j])), ratesDw[j]);
      }
      ImmutableMarketData quotesDw = builderDw.build();
      IsdaCompliantZeroRateDiscountFactors hcUp = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
          nodes, valuationDate, curveDcc, curveName, currency, quotesUp, REF_DATA);
      IsdaCompliantZeroRateDiscountFactors hcDw = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
          nodes, valuationDate, curveDcc, curveName, currency, quotesDw, REF_DATA);
      for (int j = 0; j < nCurveNode; ++j) {
        double computed = curve.getCurve().getMetadata().findInfo(CurveInfoType.JACOBIAN).get().getJacobianMatrix().get(j, i);
        double expected = 0.5 * (hcUp.getCurve().getYValues().get(j) - hcDw.getCurve().getYValues().get(j)) / EPS;
        assertEquals(computed, expected, EPS * 10d);
      }
    }
  }

  private List<CurveNode> createsNode(
      TermDepositConvention term,
      FixedIborSwapConvention swap,
      int[] termMonths,
      int[] swapYears,
      String[] idValues) {

    int nInstruments = idValues.length;
    int nTerms = termMonths.length;
    List<CurveNode> nodes = new ArrayList(nInstruments);
    for (int i = 0; i < nTerms; i++) {
      Period period = Period.ofMonths(termMonths[i]);
      nodes.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, term), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nTerms; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nTerms]);
      nodes.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), swap), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    return nodes;
  }

  public void offsetTest() {
    // date from ISDA excel
    double[] zeroRates = new double[] {0.00344732957670444, 0.00344732957670444, 0.00344732957665564, 0.00573603521085939,
        0.0084176643849198, 0.010781796487941, 0.0120279905518332, 0.0128615375747012, 0.0134582814660727, 0.0143042601975818,
        0.0151295167161045, 0.0157903795058379, 0.0163736824559949, 0.0177879283390989, 0.0189852653444261, 0.0200120415227836,
        0.0206710846569517, 0.0209227827032035, 0.0211449387252473, 0.0213424654765546, 0.0215192436195312, 0.0216783792332686,
        0.0218223878875253, 0.0219533286058241, 0.0220729029423261, 0.0221825293306998, 0.0222833996177656, 0.0223765225709555,
        0.0224627577317496, 0.0225428420251548, 0.0226174108713557, 0.0228326433450198, 0.0230608528197144, 0.0232748170088476,
        0.0234758293705513, 0.0236650313702278, 0.0238434341690096, 0.0240119367014882, 0.0241713408250857, 0.0243223640799347,
        0.0244656504877115, 0.0246017797322627, 0.0247312749980345, 0.0248546096897925, 0.0249722132155767, 0.0250950768771892,
        0.0252884986617019, 0.0254735181097892, 0.0256506710847436, 0.0258204488317648, 0.025983302527113, 0.0261396472817999,
        0.0262898656746259, 0.0264343108778737, 0.0265733094294154, 0.0267071636970361, 0.0268361540741183, 0.0269605409402411,
        0.0270805664155462, 0.0271964559337422, 0.0274246634134451, 0.0277507838016358, 0.0280662187249447, 0.028371484888637,
        0.0286670662121106, 0.0289534163886924, 0.0292309612092909, 0.0295001006749348, 0.0297612109202432, 0.0300146459672769,
        0.0302607393269689, 0.0304998054633688, 0.0307321411342168, 0.0309580266198665, 0.0311777268512541, 0.0315770230032083,
        0.0319815950322907, 0.0323755260295719, 0.0327592303654492, 0.0331331011714474, 0.0334975116837716, 0.0338528164861427,
        0.0341993526606172, 0.0345374408542415, 0.034867386268636, 0.0351894795789277, 0.0355039977878485, 0.0358112050202719,
        0.0361113532629797, 0.036314617444893, 0.0363240760018509, 0.0363333244620306, 0.0363423697486562, 0.036351218484073,
        0.0363598770059169, 0.0363683513822515, 0.0363766474257508, 0.0363847707069948, 0.0363927265669436, 0.0364005201286479,
        0.0364081563082498, 0.0364156398253248, 0.0364229752126081, 0.0364301668251504, 0.0368049737599067, 0.0372875925345602,
        0.037761022071311, 0.0382255223448261, 0.0386813436147256, 0.0391287268751859, 0.0395679042798051, 0.0399990995433007,
        0.0404225283215028, 0.0408383985709972, 0.0412469108896797, 0.0416482588393919, 0.0420426292517308, 0.0424302025180453,
        0.0427984803283626, 0.0430160187075607, 0.0432299022080761, 0.0434402221714286, 0.0436470669205816, 0.0438505218836128,
        0.0440506697113542, 0.0442475903893416, 0.0444413613443925, 0.0446320575461104, 0.0448197516035947, 0.0450045138576194,
        0.0451864124685252, 0.045365513500057, 0.0455418809993614, 0.0457483713358813, 0.0459870175650164, 0.0462221029730662,
        0.0464537066659494, 0.0466819054236241, 0.0469067737849521, 0.0471283841288746, 0.0473468067520848, 0.0475621099433725,
        0.0477743600548064, 0.0479836215699092, 0.0481899571689731, 0.0483934277916546, 0.0485940926969798, 0.0487920095208844,
        0.0489164313924592, 0.0490295826139708};
    LocalDate valuationDate = LocalDate.of(2013, 5, 29); // spot date is LocalDate.of(2013, 5, 31);
    LocalDate snapDate = LocalDate.of(2013, 5, 28);
    int nInstruments = RATES.length;
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    List<CurveNode> types = createsNode(TERM_3, SWAP_3, mmMonths, swapYears, idValues);
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < nInstruments; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), RATES[i]);
    }
    ImmutableMarketData quotes = builder.build();
    DayCount curveDCC = ACT365;
    IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, valuationDate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);

    int nCurvePoints = yc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = zeroRates.length;
    double[] times = new double[nSamplePoints];
    times[0] = 0.0;
    LocalDate tDate = valuationDate.plusDays(1);
    times[1] = curveDCC.relativeYearFraction(valuationDate, tDate);
    for (int i = 2; i < nSamplePoints; i++) {
      tDate = valuationDate.plusDays(25 * (i - 1) + 1);
      times[i] = curveDCC.relativeYearFraction(valuationDate, tDate);
    }
    for (int i = 0; i < nSamplePoints; i++) {
      double zr = yc.getCurve().yValue(times[i]);
      assertEquals(zeroRates[i], zr, TOL);
    }
    testJacobian(yc, snapDate, types, idValues, RATES);
  }

  public void regressionTest2() {
    // date from ISDA excel
    double[] sampleTimes = new double[] {0.0876712328767123, 0.167123287671233, 0.252054794520548, 0.495890410958904,
        0.747945205479452, 1, 1.4958904109589, 2.00547945205479, 2.5041095890411, 3.0027397260274, 3.5013698630137,
        4.0027397260274, 4.4986301369863, 5.0027397260274, 5.4986301369863, 6.0027397260274, 6.5013698630137, 7.01095890410959,
        7.5013698630137, 8.00821917808219, 8.50684931506849, 9.00547945205479, 9.5041095890411, 10.0054794520548,
        10.5041095890411, 11.0082191780822, 11.5041095890411, 12.0082191780822, 12.5041095890411, 13.013698630137,
        13.5041095890411, 14.0109589041096, 14.5095890410959, 15.0109589041096, 15.5068493150685, 16.0109589041096,
        16.5068493150685, 17.0109589041096, 17.5068493150685, 18.0109589041096, 18.5095890410959, 19.0164383561644,
        19.5150684931507, 20.013698630137, 20.5123287671233, 21.013698630137, 21.5095890410959, 22.013698630137, 22.5123287671233,
        23.0164383561644, 23.5123287671233, 24.0219178082192, 24.5123287671233, 25.0191780821918, 25.5178082191781,
        26.0164383561644, 26.5150684931507, 27.0191780821918, 27.5150684931507, 28.0191780821918, 28.5150684931507,
        29.0191780821918, 29.5150684931507, 30.0246575342466};
    double[] zeroRates = new double[] {0.00451091345592003, 0.0096120532508373, 0.0124886704800469, 0.0179287581253996,
        0.019476202462918, 0.0209073273478429, 0.0180925538740485, 0.0166502405937304, 0.0189037116841984, 0.0204087671935255,
        0.0220943506849952, 0.0233657744039486, 0.0246460468575126, 0.0256873833598965, 0.026666390851819, 0.0274958283375808,
        0.028228774560615, 0.0288701107678566, 0.0294694929454103, 0.0300118234002438, 0.0305061047348909, 0.0309456497124306,
        0.0313781991283657, 0.0317696564018493, 0.0321646717802045, 0.0325276505922571, 0.0329486243843157, 0.0333409374474117,
        0.0336496168922921, 0.0339423150176603, 0.0342031385938489, 0.034453517898306, 0.0346827676795623, 0.0348979210010215,
        0.0349547278282821, 0.0350088694020237, 0.0350589017641339, 0.0351067734588913, 0.035151174765217, 0.0351938059061586,
        0.0352336892661124, 0.0352720864818463, 0.0353079147726051, 0.0353419577796079, 0.0353037376607363, 0.0352671363539399,
        0.0352326134807957, 0.0351991126433607, 0.035167451913752, 0.0351368377606211, 0.0351080035690964, 0.0350796130984763,
        0.035053405709698, 0.0350273994983831, 0.0350148748938213, 0.0350028303815154, 0.0349912388762854, 0.0349799549048451,
        0.0349692583262832, 0.0349587725430485, 0.0349488194559029, 0.0349390500683469, 0.0349297655642079, 0.0349205440948243};
    LocalDate snapDate = LocalDate.of(2009, 11, 12);
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 25, 30};
    double[] rates = new double[] {0.00445, 0.009488, 0.012337, 0.017762, 0.01935, 0.020838, 0.01652, 0.02018, 0.023033,
        0.02525, 0.02696, 0.02825, 0.02931, 0.03017, 0.03092, 0.0316, 0.03231, 0.03367, 0.03419, 0.03411, 0.03412};
    int nInstruments = rates.length;
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap11Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    DayCount swapDCC = ACT360;
    FixedRateSwapLegConvention fixedLeg = FixedRateSwapLegConvention.of(Currency.USD, swapDCC, Frequency.P6M, BUS_ADJ);
    FixedIborSwapConvention swap0 = ImmutableFixedIborSwapConvention.of("standard_usd", fixedLeg, FLOATING_LEG, ADJ_0D);
    List<CurveNode> types = createsNode(TERM_0, swap0, mmMonths, swapYears, idValues);
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, snapDate, ACT365, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = yc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = sampleTimes.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = sampleTimes[i];
      double zr = yc.getCurve().yValue(time);
      assertEquals(zeroRates[i], zr, TOL);
    }
    testJacobian(yc, snapDate, types, idValues, rates);
  }

  public void differentSpotDatesTest() {
    double[][] sampleTimes = new double[][] {
        {0.0849315068493151, 0.164383561643836, 0.252054794520548, 0.504109589041096, 0.747945205479452, 1, 1.5041095890411, 2,
            2.5041095890411, 3, 3.5013698630137, 4, 4.50684931506849, 4.9972602739726, 5.50684931506849, 6.0027397260274,
            6.50684931506849, 7.0027397260274, 7.50684931506849, 8.00547945205479, 8.50684931506849, 9.00547945205479,
            9.5041095890411, 10.0027397260274, 10.5095890410959, 11, 11.5095890410959, 12.0082191780822, 12.5123287671233,
            13.0082191780822, 13.5123287671233, 14.0082191780822, 14.5095890410959, 15.0082191780822, 15.5068493150685,
            16.0054794520548, 16.5150684931507, 17.0109589041096, 17.5150684931507, 18.0109589041096, 18.5150684931507,
            19.0109589041096, 19.5150684931507, 20.013698630137, 20.5123287671233, 21.0109589041096, 21.5178082191781,
            22.0082191780822, 22.5178082191781, 23.013698630137, 23.5178082191781, 24.0164383561644, 24.5205479452055,
            25.0164383561644, 25.5178082191781, 26.0164383561644, 26.5150684931507, 27.013698630137, 27.5205479452055,
            28.0191780821918, 28.5232876712329, 29.0191780821918, 29.5232876712329, 30.0191780821918,},
        {0.0767123287671233, 0.167123287671233, 0.249315068493151, 0.498630136986301, 0.747945205479452, 0.997260273972603,
            1.4958904109589, 1.99452054794521, 2.5013698630137, 3.0027397260274, 3.5041095890411, 4.0027397260274,
            4.5041095890411, 5.0027397260274, 5.5041095890411, 6.0027397260274, 6.5013698630137, 7, 7.50684931506849,
            8.00547945205479, 8.50684931506849, 9.00547945205479, 9.50684931506849, 10.0054794520548, 10.5068493150685,
            11.0082191780822, 11.5068493150685, 12.0054794520548, 12.5041095890411, 13.0027397260274, 13.5095890410959,
            14.0082191780822, 14.5095890410959, 15.0109589041096, 15.5123287671233, 16.0109589041096, 16.5123287671233,
            17.0109589041096, 17.5095890410959, 18.0082191780822, 18.5068493150685, 19.013698630137, 19.5150684931507,
            20.013698630137, 20.5150684931507, 21.013698630137, 21.5150684931507, 22.013698630137, 22.5150684931507,
            23.013698630137, 23.5123287671233, 24.0109589041096, 24.5178082191781, 25.0164383561644, 25.5178082191781,
            26.0164383561644, 26.5178082191781, 27.0191780821918, 27.5205479452055, 28.0191780821918, 28.5178082191781,
            29.0164383561644, 29.5150684931507, 30.013698630137,},
        {0.0767123287671233, 0.161643835616438, 0.246575342465753, 0.495890410958904, 0.747945205479452, 0.997260273972603,
            1.4958904109589, 2.0027397260274, 2.5013698630137, 3.0027397260274, 3.4986301369863, 4.0027397260274, 4.4986301369863,
            5.0027397260274, 5.5013698630137, 6.0027397260274, 6.5013698630137, 7.00821917808219, 7.4986301369863,
            8.00547945205479, 8.5041095890411, 9.00547945205479, 9.5041095890411, 10.0082191780822, 10.5041095890411,
            11.0082191780822, 11.5041095890411, 12.0054794520548, 12.5041095890411, 13.0109589041096, 13.5095890410959,
            14.0109589041096, 14.5068493150685, 15.0109589041096, 15.5068493150685, 16.0109589041096, 16.5068493150685,
            17.0109589041096, 17.5095890410959, 18.0164383561644, 18.5068493150685, 19.013698630137, 19.5123287671233,
            20.013698630137, 20.5095890410959, 21.013698630137, 21.5123287671233, 22.0164383561644, 22.5123287671233,
            23.013698630137, 23.5123287671233, 24.0191780821918, 24.5095890410959, 25.0164383561644, 25.5150684931507,
            26.0191780821918, 26.5150684931507, 27.0191780821918, 27.5150684931507, 28.0191780821918, 28.5150684931507,
            29.0164383561644, 29.5150684931507, 30.0219178082192,},
        {0.0821917808219178, 0.16986301369863, 0.249315068493151, 0.495890410958904, 0.747945205479452, 1, 1.4958904109589, 2,
            2.4986301369863, 3, 3.4986301369863, 4.00547945205479, 4.5041095890411, 5.0027397260274, 5.5013698630137,
            6.0027397260274, 6.5013698630137, 7.00547945205479, 7.5013698630137, 8.00547945205479, 8.5013698630137,
            9.0027397260274, 9.5013698630137, 10.0082191780822, 10.5068493150685, 11.0082191780822, 11.5041095890411,
            12.0082191780822, 12.5041095890411, 13.0082191780822, 13.5041095890411, 14.0082191780822, 14.5068493150685,
            15.013698630137, 15.5123287671233, 16.0109589041096, 16.5095890410959, 17.0109589041096, 17.5068493150685,
            18.0109589041096, 18.5095890410959, 19.013698630137, 19.5095890410959, 20.0109589041096, 20.5095890410959,
            21.0164383561644, 21.5150684931507, 22.013698630137, 22.5123287671233, 23.0164383561644, 23.5123287671233,
            24.0164383561644, 24.5123287671233, 25.0164383561644, 25.5123287671233, 26.013698630137, 26.5205479452055,
            27.0191780821918, 27.5178082191781, 28.0191780821918, 28.5150684931507, 29.0191780821918, 29.5150684931507,
            30.0191780821918,}};
    double[][] zeroRates = new double[][] {
        {0.00451094132691394, 0.00961217974910455, 0.0124886704800469, 0.0179274411726332, 0.019476202462918, 0.0209073273478429,
            0.0179000772579215, 0.0164209678386938, 0.0186592679052116, 0.0201271386010079, 0.021797188562087, 0.0230428815658429,
            0.0243324704904329, 0.025331229290671, 0.0263158588596771, 0.0271135246391119, 0.0278487151164177, 0.0284686444021044,
            0.0290661710399708, 0.0295831721479864, 0.0300718446799346, 0.0305038794836742, 0.0309359080120768,
            0.0313248638523561, 0.0317220181775318, 0.0320714535637223, 0.0324894654570969, 0.0328641460176763,
            0.0331733043925884, 0.0334540432499793, 0.0337183143788277, 0.0339597188942556, 0.0341870155236588,
            0.0343980080434021, 0.0344540834934504, 0.0345066650263785, 0.0345571216268994, 0.0346033196626196,
            0.0346476020776184, 0.0346887439459186, 0.0347283088188042, 0.0347651813828698, 0.0348007443370411,
            0.0348341583058066, 0.0347972170482594, 0.0347620291637398, 0.034727932613995, 0.034696436809352, 0.0346651627297138,
            0.034636058999943, 0.0346077309180707, 0.0345808806544743, 0.034554845409868, 0.0345302584100671, 0.0345178592427287,
            0.0345060018158178, 0.0344945903594994, 0.0344836001780392, 0.0344728369911756, 0.0344626283203863, 0.034452670297307,
            0.0344432121915854, 0.0344339229923854, 0.0344250896444241,},
        {0.00451102494265255, 0.0096120532508373, 0.0124888839141788, 0.0179283191125014, 0.019476202462918, 0.0209079220085484,
            0.0179318202997679, 0.0164437694453777, 0.0186558416869182, 0.0201092896663371, 0.021780621447333, 0.023027554990938,
            0.0243031746704984, 0.0253182328885705, 0.0262937569866255, 0.0271023252591034, 0.0278325578499245,
            0.0284587573045192, 0.0290582636990626, 0.0295739720597311, 0.0300630229304215, 0.0304953922229926,
            0.0309244291338963, 0.0313084757037189, 0.031696115280366, 0.0320484447313153, 0.0324706505903697,
            0.032857785036212, 0.0331610176269445, 0.0334409934408067, 0.0337044029744327, 0.033944940639081, 0.0341701310108955,
            0.0343802785507912, 0.0344378289317298, 0.0344914900345785, 0.0345421783629908, 0.0345896262432547,
            0.0346343717250805, 0.0346766392888501, 0.0347166292222404, 0.0347551287305473, 0.0347912445067621,
            0.0348253682371375, 0.0347880042568088, 0.0347526128122574, 0.0347186809082781, 0.0346864674010876,
            0.0346555155111259, 0.0346260703411265, 0.0345978740690391, 0.0345708488881244, 0.0345445048132247,
            0.0345196296099635, 0.0345077802995578, 0.0344964487054076, 0.034485484517828, 0.0344749272347962, 0.0344647546173287,
            0.0344549986631012, 0.0344455838720343, 0.0344364926560736, 0.0344277086157007, 0.0344192164412062,},
        {0.00451102494265255, 0.00961230625181013, 0.0124890973580346, 0.0179287581253996, 0.019476202462918, 0.0209079220085484,
            0.0179146548564806, 0.0163995948752698, 0.0186206551386126, 0.0201101769492334, 0.0217660475691097, 0.02302882260281,
            0.0242922206616739, 0.025319832020548, 0.0262838878075548, 0.0270917555423806, 0.0278116612226671, 0.0284384450444177,
            0.0290358978669123, 0.0295764562951511, 0.0300629812643314, 0.0304978571668544, 0.030919172038549, 0.0313024406525135,
            0.0316914861635501, 0.0320510528074843, 0.0324673389902428, 0.0328532632414678, 0.0331579238389174,
            0.0334436742300171, 0.0337038681393345, 0.0339468189060175, 0.0341705955710052, 0.034382926602007, 0.0344392632577323,
            0.0344929567193703, 0.0345425749799782, 0.0345900504563705, 0.0346343206202881, 0.0346768091131062,
            0.0347157043205886, 0.0347537948601011, 0.0347893367213927, 0.0348232882333615, 0.0347864043700377,
            0.0347506934936443, 0.0347170172529163, 0.0346845215843763, 0.0346539756628263, 0.0346244304999217, 0.034596296501164,
            0.0345688959004665, 0.0345434627006801, 0.0345182248501461, 0.0345058261111373, 0.0344937742017407,
            0.0344823659106201, 0.0344711977594699, 0.034460610972114, 0.0344502328164517, 0.0344403818892104, 0.0344307644175739,
            0.0344215235693735, 0.034412444990971,},
        {0.0045109691983703, 0.0096119267570081, 0.0124888839141788, 0.0179287581253996, 0.019476202462918, 0.0209073273478429,
            0.0179335278202602, 0.0164219833090028, 0.0186299845381473, 0.0201100732741673, 0.0217671886968912,
            0.0230287833690935, 0.0243010199689606, 0.0253196453298618, 0.0262904854717679, 0.0271040384184857,
            0.0278249286240857, 0.0284494210990554, 0.0290457465231719, 0.0295762315011855, 0.0300615703697837,
            0.0304979156078269, 0.0309225851220435, 0.031310886840618, 0.0316975517117297, 0.0320510230134998, 0.0324662006814878,
            0.0328531122321117, 0.0331562801979802, 0.0334407808842474, 0.0336999187560647, 0.0339445461301522,
            0.0341697892178598, 0.0343834117442073, 0.0344401400378486, 0.0344933349470126, 0.0345433166223671,
            0.0345906185738692, 0.0346347385036731, 0.0346770994858479, 0.0347167301000603, 0.0347546832186601,
            0.0347901038970396, 0.034824131244467, 0.0347870490937025, 0.0347511590458418, 0.034717501075579, 0.0346853678721082,
            0.0346546581183228, 0.0346249635055764, 0.0345969953907137, 0.0345697475582014, 0.0345440374570194,
            0.0345189459840149, 0.0345073236854629, 0.0344960234389951, 0.0344850339837525, 0.0344746250607718,
            0.0344645933627979, 0.0344548665425021, 0.0344455824855072, 0.0344364697801176, 0.0344278093650792,
            0.0344192986850272,}};

    LocalDate[] spotDate = new LocalDate[] {
        LocalDate.of(2000, 7, 31), LocalDate.of(2013, 5, 31), LocalDate.of(2015, 1, 30), LocalDate.of(2033, 11, 29)};
    int nDates = spotDate.length;
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 25, 30};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap11Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    List<CurveNode> types = createsNode(TERM_0, SWAP_0, mmMonths, swapYears, idValues);
    double[] rates = new double[] {0.00445, 0.009488, 0.012337, 0.017762, 0.01935, 0.020838, 0.01652, 0.02018, 0.023033,
        0.02525, 0.02696, 0.02825, 0.02931, 0.03017, 0.03092, 0.0316, 0.03231, 0.03367, 0.03419, 0.03411, 0.03412,};
    int nSamplePoints = sampleTimes.length;
    int nInstruments = rates.length;
    for (int k = 0; k < nDates; ++k) {
      ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(spotDate[k]);
      for (int i = 0; i < rates.length; i++) {
        builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
      }
      ImmutableMarketData quotes = builder.build();
      IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT
          .calibrate(types, spotDate[k], ACT365, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
      int nCurvePoints = yc.getParameterCount();
      assertEquals(nInstruments, nCurvePoints);
      for (int i = 0; i < nSamplePoints; i++) {
        double time = sampleTimes[k][i];
        double zr = yc.getCurve().yValue(time);
        assertEquals(zeroRates[k][i], zr, TOL);
      }
    }
  }

  public void dayCountTest() {
    LocalDate spotDate = LocalDate.of(2009, 11, 13);
    int nMoneyMarket = 6;
    int nSwaps = 15;
    int nInstruments = nMoneyMarket + nSwaps;
    double[] rates = new double[] {0.00445, 0.009488, 0.012337, 0.017762, 0.01935, 0.020838, 0.01652, 0.02018, 0.023033,
        0.02525, 0.02696, 0.02825, 0.02931, 0.03017, 0.03092, 0.0316, 0.03231, 0.03367, 0.03419, 0.03411, 0.03412,};
    DayCount[] moneyMarketDCC = new DayCount[] {D30360, ACT_ACT, ACT360};
    DayCount[] swapDCC = new DayCount[] {D30360, ACT_ACT, ACT360};
    double[][] zeroRates = new double[][] {
        {0.00451094132691394, 0.00945460303190611, 0.0122229540868544, 0.0178301408290063, 0.0192637126928983, 0.0206242461862328,
            0.0178588147430989, 0.0164238861778704, 0.018631344987978, 0.0201117451815117, 0.0217674952346848, 0.0230301783236276,
            0.0242934910735128, 0.0253210330445365, 0.0262869428224258, 0.0271052836260002, 0.0278243965983341,
            0.0284504902511728, 0.0290513485359989, 0.0295773565758978, 0.0300639368228416, 0.0304988621348888,
            0.0309234981122104, 0.0313120284810566, 0.0316976136595328, 0.0320519270804712, 0.0324648779855183,
            0.0328537199479907, 0.0331543892482607, 0.0334413187554564, 0.0337025863249896, 0.0339452539736723,
            0.0341712396536822, 0.0343844468442421, 0.0344407813129133, 0.0344944726901091, 0.0345440890244785,
            0.0345915626578179, 0.0346355947596053, 0.0346783179464823, 0.0347178459386941, 0.0347553007048015, 0.034790841186317,
            0.0348247913802487, 0.0347878927806037, 0.034752167636536, 0.0347186587816984, 0.0346861419429677, 0.0346554111637513,
            0.0346253803814125, 0.0345977089573594, 0.0345702974092172, 0.0345444362953857, 0.0345196061141392,
            0.034507535995373, 0.0344958660484172, 0.0344846975917001, 0.0344738254448885, 0.0344635192532148, 0.034453416163592,
            0.0344438263285551, 0.0344343129715817, 0.0344254678358085, 0.0344166298790237,},
        {0.00444915928374197, 0.00948048554422632, 0.0123174428554907, 0.017684232421227, 0.0192113127644758, 0.0206227013525493,
            0.0178578803162074, 0.0164232684791861, 0.0186322669831205, 0.020113699749959, 0.0217678904337317, 0.0230293843399428,
            0.0242925501835449, 0.0253199726652561, 0.0262857612109845, 0.0271039993039063, 0.0278237617886532,
            0.0284504209376168, 0.0290505062276185, 0.0295758375663676, 0.0300623424281604, 0.0304972003578786,
            0.0309217696564316, 0.0313102390160184, 0.0316963497583253, 0.032051146118778, 0.0324633901436513, 0.0328515664951015,
            0.0331523890961102, 0.0334394648986057, 0.033700865679316, 0.0339436570557279, 0.0341697579579161, 0.0343830738553501,
            0.0344392890347298, 0.0344928667194298, 0.034542377990283, 0.0345897510973155, 0.0346336899603035, 0.0346763226800328,
            0.0347157669709816, 0.0347531424259208, 0.0347886076498017, 0.0348224859535626, 0.0347855935225375,
            0.0347498743509143, 0.0347163710980069, 0.0346838596953639, 0.0346531340536459, 0.0346231082917816,
            0.0345954414937712, 0.0345680345282262, 0.0345421777377941, 0.0345173517075981, 0.0345052793810392,
            0.0344936072994881, 0.0344824367999049, 0.0344715626644267, 0.0344612545876073, 0.0344511496499889,
            0.0344415580608378, 0.0344320429637392, 0.0344231962100678, 0.0344143566366979,},
        {0.00451094132691394, 0.0096120532508373, 0.0124882436409548, 0.0179287581253996, 0.019476202462918, 0.0209061381614906,
            0.0181040003596387, 0.0166500255791151, 0.0188996867376634, 0.020408389323302, 0.0220860526785435, 0.0233654469588231,
            0.0246457399933126, 0.0256870932356245, 0.0266661166481783, 0.0274955676222623, 0.0282303573824345, 0.028870099985772,
            0.0294788395706417, 0.030011747119614, 0.0305046822913288, 0.0309452878948724, 0.0313755978170794, 0.0317693196911606,
            0.0321643461543265, 0.0325273351521884, 0.0329459710324609, 0.0333401660693457, 0.0336461396134401,
            0.0339381309745242, 0.0342040076840362, 0.0344509563430373, 0.0346809287391722, 0.0348978972147147,
            0.0349547044083665, 0.0350088463313092, 0.0350588790161168, 0.035106751019636, 0.03515115261234, 0.035194234306507,
            0.0352340939938567, 0.0352718630578354, 0.0353077017736973, 0.0353419368572384, 0.035303919264229, 0.0352671107132153,
            0.0352325856630051, 0.0351990827129553, 0.0351674199867719, 0.0351364784856934, 0.0351079678932113,
            0.0350797250576823, 0.035053079675205, 0.0350274964895541, 0.0350149676071521, 0.0350028541063757, 0.0349912611565855,
            0.0349799757789108, 0.0349692778673166, 0.0349587907773192, 0.0349488364497969, 0.0349389615071325, 0.034929780183562,
            0.0349206063118399,}};
    double[] sampleTimes = new double[] {0.0849315068493151, 0.167123287671233, 0.257534246575342, 0.495890410958904,
        0.747945205479452, 1.00547945205479, 1.4958904109589, 2.0027397260274, 2.5013698630137, 3.0027397260274, 3.4986301369863,
        4.0027397260274, 4.4986301369863, 5.0027397260274, 5.4986301369863, 6.0027397260274, 6.5013698630137, 7.00821917808219,
        7.50684931506849, 8.00547945205479, 8.5041095890411, 9.00547945205479, 9.5013698630137, 10.0054794520548,
        10.5041095890411, 11.0082191780822, 11.5041095890411, 12.013698630137, 12.5041095890411, 13.0109589041096,
        13.5095890410959, 14.0082191780822, 14.5068493150685, 15.0109589041096, 15.5068493150685, 16.0109589041096,
        16.5068493150685, 17.0109589041096, 17.5068493150685, 18.0164383561644, 18.5150684931507, 19.013698630137,
        19.5123287671233, 20.013698630137, 20.5095890410959, 21.013698630137, 21.5095890410959, 22.013698630137, 22.5123287671233,
        23.0219178082192, 23.5123287671233, 24.0191780821918, 24.5178082191781, 25.0164383561644, 25.5150684931507,
        26.0164383561644, 26.5150684931507, 27.0191780821918, 27.5150684931507, 28.0191780821918, 28.5150684931507,
        29.0246575342466, 29.5150684931507, 30.0219178082192,};
    for (int ii = 0; ii < 3; ++ii) {
      List<CurveNode> types = new ArrayList(nInstruments);
      int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
      int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 25, 30};
      TermDepositConvention term0 = ImmutableTermDepositConvention.builder()
          .businessDayAdjustment(BUS_ADJ)
          .currency(Currency.USD)
          .dayCount(moneyMarketDCC[ii])
          .name("standar_usd")
          .spotDateOffset(ADJ_0D)
          .build();
      FixedRateSwapLegConvention fixedLeg = FixedRateSwapLegConvention.of(Currency.USD, swapDCC[ii], Frequency.P6M, BUS_ADJ);
      FixedIborSwapConvention swap0 = ImmutableFixedIborSwapConvention.of("standard_usd", fixedLeg, FLOATING_LEG, ADJ_0D);
      String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
          "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap11Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
      for (int i = 0; i < nMoneyMarket; i++) {
        Period period = Period.ofMonths(mmMonths[i]);
        types.add(TermDepositCurveNode.of(
            TermDepositTemplate.of(period, term0), QuoteId.of(StandardId.of("OG", idValues[i]))));
      }
      for (int i = nMoneyMarket; i < nInstruments; i++) {
        Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
        types.add(FixedIborSwapCurveNode.of(
            FixedIborSwapTemplate.of(Tenor.of(period), swap0), QuoteId.of(StandardId.of("OG", idValues[i]))));
      }
      ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(spotDate);
      for (int i = 0; i < rates.length; i++) {
        builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
      }
      ImmutableMarketData quotes = builder.build();
      IsdaCompliantZeroRateDiscountFactors yc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
          types, spotDate, ACT365, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
      int nCurvePoints = yc.getParameterCount();
      assertEquals(nInstruments, nCurvePoints);
      int nSamplePoints = sampleTimes.length;
      for (int i = 0; i < nSamplePoints; i++) {
        double time = sampleTimes[i];
        double zr = yc.getCurve().yValue(time);
        assertEquals(zeroRates[ii][i], zr, TOL);
      }
    }
  }

  public void onlyMoneyOrSwapTest() {
    // date from ISDA excel
    double[] sampleTimes = new double[] {0.0767123287671233, 0.167123287671233, 0.249315068493151, 0.498630136986301,
        0.747945205479452, 0.997260273972603, 1.4958904109589, 1.99452054794521, 2.5013698630137, 3.0027397260274,
        3.5041095890411, 4.0027397260274, 4.5041095890411, 5.0027397260274, 5.5041095890411, 6.0027397260274, 6.5013698630137, 7,
        7.50684931506849, 8.00547945205479, 8.50684931506849, 9.00547945205479, 9.50684931506849, 10.0054794520548,
        10.5068493150685, 11.0082191780822, 11.5068493150685, 12.0054794520548, 12.5041095890411, 13.0027397260274,
        13.5095890410959, 14.0082191780822, 14.5095890410959, 15.0109589041096, 15.5123287671233, 16.0109589041096,
        16.5123287671233, 17.0109589041096, 17.5095890410959, 18.0082191780822, 18.5068493150685, 19.013698630137,
        19.5150684931507, 20.013698630137, 20.5150684931507, 21.013698630137, 21.5150684931507, 22.013698630137, 22.5150684931507,
        23.013698630137, 23.5123287671233, 24.0109589041096, 24.5178082191781, 25.0164383561644, 25.5178082191781,
        26.0164383561644, 26.5178082191781, 27.0191780821918, 27.5205479452055, 28.0191780821918, 28.5178082191781,
        29.0164383561644, 29.5150684931507, 30.013698630137};
    double[] zeroRates = new double[] {0.00344732957665484, 0.00645427070262317, 0.010390833731528, 0.0137267241507424,
        0.016406009142171, 0.0206548075787697, 0.0220059788254565, 0.0226815644487997, 0.0241475224808774, 0.0251107341245228,
        0.0263549710022889, 0.0272832610741453, 0.0294785565070328, 0.0312254350680597, 0.0340228731758456, 0.0363415444446394,
        0.0364040719835966, 0.0364576914896066, 0.0398713425199977, 0.0428078389323812, 0.0443206903065534, 0.0456582004054368,
        0.0473373527805339, 0.0488404232471453, 0.0496433764260127, 0.0503731885238783, 0.0510359350109291, 0.0516436290741354,
        0.0526405492486405, 0.0535610094687589, 0.05442700569164, 0.0552178073994544, 0.0559581527041068, 0.0566490425640605,
        0.0572429526830672, 0.0577967261153023, 0.0583198210222109, 0.0588094750567186, 0.0592712408001043, 0.0597074348516306,
        0.0601201241459759, 0.0605174325075768, 0.0608901411604128, 0.0612422922398251, 0.0618707980423834, 0.0624661234885966,
        0.0630368977571603, 0.0635787665840882, 0.064099413535239, 0.0645947156962813, 0.0650690099353217, 0.0655236050526131,
        0.0659667431709796, 0.0663851731522577, 0.0668735344788778, 0.0673405584796377, 0.0677924400667054, 0.0682275513575991,
        0.0686468089170376, 0.0690488939824011, 0.0694369182384849, 0.06981160656508, 0.0701736348572483, 0.0705236340943412};
    LocalDate spotDate = LocalDate.of(2013, 5, 31);
    int nMoneyMarket1 = 0;
    int nSwaps1 = 14;
    int nInstruments1 = nMoneyMarket1 + nSwaps1;
    List<CurveNode> types1 = new ArrayList(nInstruments1);
    int[] mmMonths1 = new int[] {};
    int[] swapYears1 = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    String[] idValues1 = new String[] {"swap2Y", "swap3Y", "swap4Y", "swap5Y", "swap6Y", "swap7Y", "swap8Y", "swap9Y",
        "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    for (int i = 0; i < nMoneyMarket1; i++) {
      Period period = Period.ofMonths(mmMonths1[i]);
      TermDepositConvention convention = TermDepositConventions.USD_SHORT_DEPOSIT_T2;
      types1.add(
          TermDepositCurveNode.of(TermDepositTemplate.of(period, convention), QuoteId.of(StandardId.of("OG", idValues1[i]))));
    }
    for (int i = nMoneyMarket1; i < nInstruments1; i++) {
      Period period = Period.ofYears(swapYears1[i - nMoneyMarket1]);
      FixedIborSwapConvention convention = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
      types1.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), convention), QuoteId.of(StandardId.of("OG", idValues1[i]))));
    }
    int nMoneyMarket2 = 6;
    int nSwaps2 = 0;
    int nInstruments2 = nMoneyMarket2 + nSwaps2;
    List<CurveNode> types2 = new ArrayList(nInstruments2);
    int[] mmMonths2 = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears2 = new int[] {};
    String[] idValues2 = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M"};
    for (int i = 0; i < nMoneyMarket2; i++) {
      Period period = Period.ofMonths(mmMonths2[i]);
      types2.add(
          TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_0), QuoteId.of(StandardId.of("OG", idValues2[i]))));
    }
    for (int i = nMoneyMarket2; i < nInstruments2; i++) {
      Period period = Period.ofYears(swapYears2[i - nMoneyMarket2]);
      types2.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_0), QuoteId.of(StandardId.of("OG", idValues2[i]))));
    }
    double[] rates1 = new double[] {0.0227369218210212, 0.0251978805237614, 0.0273223815467694, 0.0310882447627048,
        0.0358397743454067, 0.036047665095421, 0.0415916567616181, 0.044066373237682, 0.046708518178509, 0.0491196954851753,
        0.0529297239911766, 0.0562025436376854, 0.0589772202773522, 0.0607471217692999};
    ImmutableMarketDataBuilder builder1 = ImmutableMarketData.builder(spotDate);
    for (int i = 0; i < rates1.length; i++) {
      builder1.addValue(QuoteId.of(StandardId.of("OG", idValues1[i])), rates1[i]);
    }
    ImmutableMarketData quotes1 = builder1.build();
    double[] rates2 = new double[] {0.00340055550701297, 0.00636929056400781, 0.0102617798438113, 0.0135851258907251,
        0.0162809551414651, 0.020583125112332};
    ImmutableMarketDataBuilder builder2 = ImmutableMarketData.builder(spotDate);
    for (int i = 0; i < rates2.length; i++) {
      builder2.addValue(QuoteId.of(StandardId.of("OG", idValues2[i])), rates2[i]);
    }
    ImmutableMarketData quotes2 = builder2.build();
    IsdaCompliantZeroRateDiscountFactors hc1 =
        CALIBRATOR.calibrate(types1, spotDate, ACT365, CurveName.of("yield"), Currency.USD, quotes1, REF_DATA);
    int nCurvePoints1 = hc1.getParameterCount();
    assertEquals(nInstruments1, nCurvePoints1);
    IsdaCompliantZeroRateDiscountFactors hc2 =
        CALIBRATOR.calibrate(types2, spotDate, ACT365, CurveName.of("yield"), Currency.USD, quotes2, REF_DATA);
    int nCurvePoints2 = hc2.getParameterCount();
    assertEquals(nInstruments2, nCurvePoints2);
    double ref1 = 0.0;
    double ref2 = 0.0;
    int nSamplePoints = sampleTimes.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = sampleTimes[i];
      double zr1 = hc1.getCurve().yValue(time);
      double zr2 = hc2.getCurve().yValue(time);
      if (time < 1.) {
        assertEquals(zeroRates[i], zr2, TOL);
        if (i > 0) {
          assertTrue(zr1 == ref1);
        }
      }
      assertTrue(zr1 >= ref1);
      assertTrue(zr2 >= ref2);
      ref1 = zr1;
      ref2 = zr2;
    }
  }

  public void anotherConventionTest() {
    // date from ISDA excel
    double[] sampleTimes = new double[] {0.0849315068493151, 0.167123287671233, 0.257534246575342, 0.495890410958904,
        0.747945205479452, 1.00547945205479, 1.4958904109589, 2.0027397260274, 2.5013698630137, 3.0027397260274, 3.4986301369863,
        4.0027397260274, 4.4986301369863, 5.0027397260274, 5.4986301369863, 6.0027397260274, 6.5013698630137, 7.00821917808219,
        7.50684931506849, 8.00547945205479, 8.5041095890411, 9.00547945205479, 9.5013698630137, 10.0054794520548,
        10.5041095890411, 11.0082191780822, 11.5041095890411, 12.013698630137, 12.5041095890411, 13.0109589041096,
        13.5095890410959, 14.0082191780822, 14.5068493150685, 15.0109589041096, 15.5068493150685, 16.0109589041096,
        16.5068493150685, 17.0109589041096, 17.5068493150685, 18.0164383561644, 18.5150684931507, 19.013698630137,
        19.5123287671233, 20.013698630137, 20.5095890410959, 21.013698630137, 21.5095890410959, 22.013698630137, 22.5123287671233,
        23.0219178082192, 23.5123287671233, 24.0191780821918, 24.5178082191781, 25.0164383561644, 25.5150684931507,
        26.0164383561644, 26.5150684931507, 27.0191780821918, 27.5150684931507, 28.0191780821918, 28.5150684931507,
        29.0246575342466, 29.5150684931507, 30.0219178082192,};
    double[] zeroRates = new double[] {0.00451094132691394, 0.0096120532508373, 0.0124882436409548, 0.0179287581253996,
        0.019476202462918, 0.0209061381614906, 0.0181040003596387, 0.0166500255791151, 0.0188996867376634, 0.020408389323302,
        0.0220860526785435, 0.0233654469588231, 0.0246457399933126, 0.0256870932356245, 0.0266661166481783, 0.0274955676222623,
        0.0282303573824345, 0.028870099985772, 0.0294788395706417, 0.030011747119614, 0.0305046822913288, 0.0309452878948724,
        0.0313755978170794, 0.0317693196911606, 0.0321643461543265, 0.0325273351521884, 0.0329459710324609, 0.0333401660693457,
        0.0336461396134401, 0.0339381309745242, 0.0342040076840362, 0.0344509563430373, 0.0346809287391722, 0.0348978972147147,
        0.0349547044083665, 0.0350088463313092, 0.0350588790161168, 0.035106751019636, 0.03515115261234, 0.035194234306507,
        0.0352340939938567, 0.0352718630578354, 0.0353077017736973, 0.0353419368572384, 0.035303919264229, 0.0352671107132153,
        0.0352325856630051, 0.0351990827129553, 0.0351674199867719, 0.0351364784856934, 0.0351079678932113, 0.0350797250576823,
        0.035053079675205, 0.0350274964895541, 0.0350149676071521, 0.0350028541063757, 0.0349912611565855, 0.0349799757789108,
        0.0349692778673166, 0.0349587907773192, 0.0349488364497969, 0.0349389615071325, 0.034929780183562, 0.0349206063118399,};
    LocalDate spotDate = LocalDate.of(2009, 11, 13);
    DayCount moneyMarketDCC = ACT360;
    DayCount swapDCC = ACT360;
    int nMoneyMarket = 6;
    int nSwaps = 15;
    int nInstruments = nMoneyMarket + nSwaps;
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 25, 30};
    double[] rates = new double[] {0.00445, 0.009488, 0.012337, 0.017762, 0.01935, 0.020838, 0.01652, 0.02018, 0.023033,
        0.02525, 0.02696, 0.02825, 0.02931, 0.03017, 0.03092, 0.0316, 0.03231, 0.03367, 0.03419, 0.03411, 0.03412,};
    List<CurveNode> types = new ArrayList(nInstruments);
    BusinessDayAdjustment busAdj = BusinessDayAdjustment.of(FOLLOWING, CALENDAR);
    TermDepositConvention term0 = ImmutableTermDepositConvention.builder()
        .businessDayAdjustment(busAdj)
        .currency(Currency.USD)
        .dayCount(moneyMarketDCC)
        .name("standar_usd")
        .spotDateOffset(ADJ_0D)
        .build();
    FixedRateSwapLegConvention fixedLeg = FixedRateSwapLegConvention.of(Currency.USD, swapDCC, Frequency.P6M, busAdj);
    FixedIborSwapConvention swap0 = ImmutableFixedIborSwapConvention.of("standard_usd", fixedLeg, FLOATING_LEG, ADJ_0D);
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap11Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(
          TermDepositTemplate.of(period, term0), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), swap0), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(spotDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    IsdaCompliantZeroRateDiscountFactors yc =
        CALIBRATOR.calibrate(types, spotDate, ACT365, CurveName.of("test"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = yc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = sampleTimes.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = sampleTimes[i];
      double zr = yc.zeroRate(time);
      assertEquals(zeroRates[i], zr, TOL);
    }
  }

  public void negativeRatesTest() {
    double[] zeroRates = new double[] {-0.021919169445972334, -0.019346997617199872, -0.015724925901986937, -0.011927193964450558,
        -0.00914226190648963, -0.004858408228040751, -0.003177040569633026, -0.0023951619743151106, -9.116564810142423E-4,
        9.093250064676298E-5, 0.0013160693468185394, 0.002245144996961292, 0.004348618241058566, 0.006057057096853802,
        0.008717668264829868, 0.010948502025313694, 0.011091666959414275, 0.01117800566392536, 0.014281641157698574,
        0.017045075975902716, 0.01848908647401718, 0.019744573304606557, 0.02128960911961616, 0.022685934660937972,
        0.023460415257638496, 0.024151053264158357, 0.024778225537656486, 0.02535330043380334, 0.026234633639786776,
        0.027057937503363945, 0.02783252614995365, 0.028539857207979635, 0.029202057633787304, 0.02982002273273868,
        0.03033354931993612, 0.030810625998748745, 0.03126127307996477, 0.03168311079725512, 0.03208092270808424,
        0.03245670456634349, 0.032812237039494464, 0.033154518815006674, 0.03347560790290696, 0.03377898660911919,
        0.03424571865533365, 0.034693621774103636, 0.03512305340117343, 0.035530737532750656, 0.03592245502892328,
        0.03629510394570572, 0.036651947198807336, 0.03699396948612518, 0.03732737190748109, 0.037642184746061194,
        0.03796035404268169, 0.03826492785153772, 0.038559626402149924, 0.03884338805664329, 0.03911681056075889,
        0.03937903387185762, 0.039632087302541336, 0.039876443589539956, 0.040112543523761486, 0.0403407986039386,
        0.010087974088676337, 0.010087974088676337, 0.010087974088676337, 0.010087974088676337, 0.010087974088676337,
        0.010087974088676347, 0.025023916871786102, 0.03249188826334116, 0.037031564191394764, 0.040014370394980396,
        0.042143613603638455, 0.043732181959459715, 0.044974847742655547, 0.04596368331470032, 0.046777310890392165,
        0.0474516903799703, 0.048022625228079215, 0.048512221412895906, 0.04894322962079566, 0.04931399219873278,
        0.04964296845438076, 0.04993381597035878, 0.05019549998291769, 0.050429742876353094, 0.0506428560184085,
        0.05083655666419901, 0.05101245731439732, 0.05117374640898808, 0.05132217195615749, 0.05145921383304254,
        0.05158814688757879, 0.05170588467560495, 0.05181611030825588, 0.05191897281231579, 0.05201518613296244,
        0.05210489731836933, 0.0521896385888013, 0.052268962444584066, 0.052343768402775714, 0.05241443175199401,
        0.05248128733524406, 0.052545651210461786, 0.05260602993777339, 0.05266307834229664, 0.052717644115469024,
        0.052769329228207186, 0.05281888285069453, 0.052865926953356714, 0.05291112860992654, 0.05295412987620159,
        0.052995307271476395, 0.05303477442349697, 0.0530732468989338, 0.053109574257176176, 0.05314466978617466,
        0.05317823193261876, 0.05321070588679645, 0.053241974662792325, 0.053272104128483135, 0.053300999512499274,
        0.05332888443251486, 0.053355810981188204, 0.053381827730946, 0.05340698002639876};
    LocalDate valuationDate = LocalDate.of(2013, 5, 31);
    LocalDate snapDate = LocalDate.of(2013, 5, 30);
    int nMoneyMarket = 6;
    int nSwaps = 14;
    int nInstruments = nMoneyMarket + nSwaps;
    List<CurveNode> types = new ArrayList(nInstruments);
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
        "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    double[] rates = new double[] {-0.021599444492987032, -0.018630709435992193, -0.0147382201561887, -0.011414874109274902,
        -0.008719044858534902, -0.004416874887668003, -0.0022630781789788022, 1.978805237613998E-4, 0.002322381546769399,
        0.006088244762704798, 0.0108397743454067, 0.011047665095420996, 0.016591656761618098, 0.019066373237681997,
        0.021708518178508995, 0.0241196954851753, 0.027929723991176596, 0.031202543637685397, 0.0339772202773522,
        0.0357471217692999};
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    DayCount curveDCC = ACT365;
    IsdaCompliantZeroRateDiscountFactors hc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, valuationDate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = hc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = SAMPLE_TIMES.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = SAMPLE_TIMES[i];
      double zr = hc.getCurve().yValue(time);
      assertEquals(zeroRates[i], zr, TOL);
    }
  }

  public void twoNodesTest() {
    double[] zeroRates = new double[] {0.010087974088676337, 0.010087974088676337, 0.010087974088676337, 0.010087974088676337,
        0.010087974088676337, 0.010087974088676347, 0.025023916871786102, 0.03249188826334116, 0.037031564191394764,
        0.040014370394980396, 0.042143613603638455, 0.043732181959459715, 0.044974847742655547, 0.04596368331470032,
        0.046777310890392165, 0.0474516903799703, 0.048022625228079215, 0.048512221412895906, 0.04894322962079566,
        0.04931399219873278, 0.04964296845438076, 0.04993381597035878, 0.05019549998291769, 0.050429742876353094,
        0.0506428560184085, 0.05083655666419901, 0.05101245731439732, 0.05117374640898808, 0.05132217195615749,
        0.05145921383304254, 0.05158814688757879, 0.05170588467560495, 0.05181611030825588, 0.05191897281231579,
        0.05201518613296244, 0.05210489731836933, 0.0521896385888013, 0.052268962444584066, 0.052343768402775714,
        0.05241443175199401, 0.05248128733524406, 0.052545651210461786, 0.05260602993777339, 0.05266307834229664,
        0.052717644115469024, 0.052769329228207186, 0.05281888285069453, 0.052865926953356714, 0.05291112860992654,
        0.05295412987620159, 0.052995307271476395, 0.05303477442349697, 0.0530732468989338, 0.053109574257176176,
        0.05314466978617466, 0.05317823193261876, 0.05321070588679645, 0.053241974662792325, 0.053272104128483135,
        0.053300999512499274, 0.05332888443251486, 0.053355810981188204, 0.053381827730946, 0.05340698002639876};
    LocalDate spotDate = LocalDate.of(2013, 5, 31);
    int nMoneyMarket = 1;
    int nSwaps = 1;
    int nInstruments = nMoneyMarket + nSwaps;
    List<CurveNode> types = new ArrayList(nInstruments);
    int[] mmMonths = new int[] {12};
    int[] swapYears = new int[] {10};
    String[] idValues = new String[] {"mm12M", "swap10Y"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_0), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_0), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    double[] rates = new double[] {0.01, 0.05};
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(spotDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    DayCount curveDCC = ACT365;
    IsdaCompliantZeroRateDiscountFactors hc = IsdaCompliantDiscountCurveCalibrator.DEFAULT.calibrate(
        types, spotDate, curveDCC, CurveName.of("yield"), Currency.USD, quotes, REF_DATA);
    int nCurvePoints = hc.getParameterCount();
    assertEquals(nInstruments, nCurvePoints);
    int nSamplePoints = SAMPLE_TIMES.length;
    for (int i = 0; i < nSamplePoints; i++) {
      double time = SAMPLE_TIMES[i];
      double zr = hc.getCurve().yValue(time);
      assertEquals(zeroRates[i], zr, 1e-14);
    }
  }

  //-------------------------------------------------------------------------
  public void OverlappingInstrumentsTest() {
    LocalDate valuationDate = LocalDate.of(2013, 5, 31);
    LocalDate snapDate = LocalDate.of(2013, 5, 30);
    int nMoneyMarket = 6;
    int nSwaps = 14;
    int nInstruments = nMoneyMarket + nSwaps;
    List<CurveNode> types = new ArrayList(nInstruments);
    int[] mmMonths = new int[] {1, 2, 3, 6, 9, 12};
    int[] swapYears = new int[] {1, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    double[] rates = new double[] {0.00340055550701297, 0.00636929056400781, 0.0102617798438113, 0.0135851258907251,
        0.0162809551414651, 0.020583125112332, 0.0227369218210212, 0.0251978805237614, 0.0273223815467694, 0.0310882447627048,
        0.0358397743454067, 0.036047665095421, 0.0415916567616181, 0.044066373237682, 0.046708518178509, 0.0491196954851753,
        0.0529297239911766, 0.0562025436376854, 0.0589772202773522, 0.0607471217692999};
    String[] idValues =
        new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "mm12M", "swap1Y", "swap2Y", "swap3Y", "swap4Y", "swap5Y",
            "swap6Y", "swap7Y", "swap8Y", "swap9Y", "swap10Y", "swap12Y", "swap15Y", "swap20Y", "swap25Y", "swap30Y"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    assertThrowsIllegalArg(
        () -> CALIBRATOR.calibrate(types, valuationDate, ACT365, CurveName.of("test"), Currency.USD, quotes, REF_DATA));
  }

  public void oneNodeTest() {
    LocalDate valuationDate = LocalDate.of(2013, 5, 31);
    LocalDate snapDate = LocalDate.of(2013, 5, 31);
    int nMoneyMarket = 1;
    int nSwaps = 0;
    int nInstruments = nMoneyMarket + nSwaps;
    List<CurveNode> types = new ArrayList(nInstruments);
    int[] mmMonths = new int[] {1};
    int[] swapYears = new int[] {};
    double[] rates = new double[] {0.00340055550701297};
    String[] idValues = new String[] {"mm1M"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    ImmutableMarketData quotes = builder.build();
    assertThrowsIllegalArg(
        () -> CALIBRATOR.calibrate(types, valuationDate, ACT365, CurveName.of("test"), Currency.USD, quotes, REF_DATA));
  }

  public void moneyMarketAfterSwapTest() {
    LocalDate valuationDate = LocalDate.of(2013, 5, 31);
    LocalDate snapDate = LocalDate.of(2013, 5, 30);
    int nMoneyMarket = 5;
    int nSwaps = 1;
    int nInstruments = nMoneyMarket + nSwaps;
    List<CurveNode> types = new ArrayList(nInstruments + 1);
    int[] mmMonths = new int[] {1, 2, 3, 6, 9};
    int[] swapYears = new int[] {1};
    double[] rates = new double[] {0.00340055550701297, 0.00636929056400781, 0.0102617798438113, 0.0135851258907251,
        0.0162809551414651, 0.0227369218210212, 0.0251978805237614};
    String[] idValues = new String[] {"mm1M", "mm2M", "mm3M", "mm6M", "mm9M", "swap1Y", "mm18M"};
    for (int i = 0; i < nMoneyMarket; i++) {
      Period period = Period.ofMonths(mmMonths[i]);
      types.add(TermDepositCurveNode.of(TermDepositTemplate.of(period, TERM_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    for (int i = nMoneyMarket; i < nInstruments; i++) {
      Period period = Period.ofYears(swapYears[i - nMoneyMarket]);
      types.add(FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Tenor.of(period), SWAP_3), QuoteId.of(StandardId.of("OG", idValues[i]))));
    }
    types.add(TermDepositCurveNode.of(
        TermDepositTemplate.of(Period.ofMonths(18), TERM_3), QuoteId.of(StandardId.of("OG", idValues[nInstruments]))));
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(snapDate);
    for (int i = 0; i < rates.length; i++) {
      builder.addValue(QuoteId.of(StandardId.of("OG", idValues[i])), rates[i]);
    }
    builder.addValue(QuoteId.of(StandardId.of("OG", idValues[nInstruments])), rates[nInstruments]);
    ImmutableMarketData quotes = builder.build();
    assertThrowsIllegalArg(
        () -> CALIBRATOR.calibrate(types, valuationDate, ACT365, CurveName.of("test"), Currency.USD, quotes, REF_DATA));
  }
}
