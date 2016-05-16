/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.marketdata;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.market.MarketDataBox;
import com.opengamma.strata.basics.market.MarketDataId;
import com.opengamma.strata.basics.market.MarketDataNotFoundException;
import com.opengamma.strata.basics.market.ObservableId;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.MapStream;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;

/**
 * An immutable set of market data across one or more scenarios.
 * <p>
 * This is the standard immutable implementation of {@link CalculationMarketData}.
 */
@BeanDefinition(builderScope = "private")
public final class ImmutableCalculationMarketData
    implements CalculationMarketData, ImmutableBean, Serializable {

  /** An empty instance. */
  static final ImmutableCalculationMarketData EMPTY =
      new ImmutableCalculationMarketData(0, MarketDataBox.empty(), ImmutableMap.of(), ImmutableMap.of());

  /**
   * The number of scenarios.
   */
  @PropertyDefinition(validate = "ArgChecker.notNegative", overrideGet = true)
  private final int scenarioCount;
  /**
   * The valuation date associated with each scenario.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final MarketDataBox<LocalDate> valuationDate;
  /**
   * The individual items of market data.
   */
  @PropertyDefinition(validate = "notNull", builderType = "Map<? extends MarketDataId<?>, MarketDataBox<?>>")
  private final ImmutableMap<MarketDataId<?>, MarketDataBox<?>> values;
  /**
   * The time-series of market data values.
   * <p>
   * If a request is made for a time-series that is not in the map, an empty series will be returned.
   */
  @PropertyDefinition(validate = "notNull", builderType = "Map<? extends ObservableId, LocalDateDoubleTimeSeries>")
  private final ImmutableMap<ObservableId, LocalDateDoubleTimeSeries> timeSeries;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance from a valuation date, map of values and time-series.
   * <p>
   * The valuation date and map of values must have the same number of scenarios.
   * 
   * @param scenarioCount  the number of scenarios
   * @param valuationDate  the valuation dates associated with all scenarios
   * @param values  the market data values, one for each scenario
   * @param timeSeries  the time-series
   * @return a set of market data containing the values in the map
   */
  public static ImmutableCalculationMarketData of(
      int scenarioCount,
      LocalDate valuationDate,
      Map<? extends MarketDataId<?>, MarketDataBox<?>> values,
      Map<? extends ObservableId, LocalDateDoubleTimeSeries> timeSeries) {

    return of(scenarioCount, MarketDataBox.ofSingleValue(valuationDate), values, timeSeries);
  }

  /**
   * Obtains an instance from a valuation date, map of values and time-series.
   * <p>
   * The valuation date and map of values must have the same number of scenarios.
   * 
   * @param scenarioCount  the number of scenarios
   * @param valuationDate  the valuation dates associated with the market data, one for each scenario
   * @param values  the market data values, one for each scenario
   * @param timeSeries  the time-series
   * @return a set of market data containing the values in the map
   */
  public static ImmutableCalculationMarketData of(
      int scenarioCount,
      MarketDataBox<LocalDate> valuationDate,
      Map<? extends MarketDataId<?>, MarketDataBox<?>> values,
      Map<? extends ObservableId, LocalDateDoubleTimeSeries> timeSeries) {

    MapStream.of(values).forEach((key, value) -> checkType(key, value, scenarioCount));
    return new ImmutableCalculationMarketData(scenarioCount, valuationDate, values, timeSeries);
  }

  // checks the value is an instance of the market data type of the id
  static void checkType(MarketDataId<?> id, MarketDataBox<?> box, int scenarioCount) {
    if (box == null) {
      throw new IllegalArgumentException(Messages.format(
          "Value for identifier '{}' must not be null", id));
    }
    if (box.getScenarioCount() != scenarioCount) {
      throw new IllegalArgumentException(Messages.format(
          "Value for identifier '{}' should have had {} scenarios but had {}", id, scenarioCount, box.getScenarioCount()));
    }
    if (box.getScenarioCount() > 0 && !id.getMarketDataType().isInstance(box.getValue(0))) {
      throw new ClassCastException(Messages.format(
          "Value for identifier '{}' does not implement expected type '{}': '{}'",
          id, id.getMarketDataType().getSimpleName(), box));
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean containsValue(MarketDataId<?> id) {
    // overridden for performance
    return values.containsKey(id);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> MarketDataBox<T> getValue(MarketDataId<T> id) {
    // overridden for performance
    // no type check against id.getMarketDataType() as checked in factory
    @SuppressWarnings("unchecked")
    MarketDataBox<T> value = (MarketDataBox<T>) values.get(id);
    if (value == null) {
      throw new MarketDataNotFoundException(msgValueNotFound(id));
    }
    return value;
  }

  // extracted to aid inlining performance
  private String msgValueNotFound(MarketDataId<?> id) {
    return Messages.format(
        "Market data not found for identifier '{}' of type '{}'", id, id.getClass().getSimpleName());
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Optional<MarketDataBox<T>> findValue(MarketDataId<T> id) {
    // no type check against id.getMarketDataType() as checked in factory
    @SuppressWarnings("unchecked")
    MarketDataBox<T> value = (MarketDataBox<T>) values.get(id);
    return Optional.ofNullable(value);
  }

  @Override
  public LocalDateDoubleTimeSeries getTimeSeries(ObservableId id) {
    LocalDateDoubleTimeSeries found = timeSeries.get(id);
    return found == null ? LocalDateDoubleTimeSeries.empty() : found;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutableCalculationMarketData}.
   * @return the meta-bean, not null
   */
  public static ImmutableCalculationMarketData.Meta meta() {
    return ImmutableCalculationMarketData.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutableCalculationMarketData.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  private ImmutableCalculationMarketData(
      int scenarioCount,
      MarketDataBox<LocalDate> valuationDate,
      Map<? extends MarketDataId<?>, MarketDataBox<?>> values,
      Map<? extends ObservableId, LocalDateDoubleTimeSeries> timeSeries) {
    ArgChecker.notNegative(scenarioCount, "scenarioCount");
    JodaBeanUtils.notNull(valuationDate, "valuationDate");
    JodaBeanUtils.notNull(values, "values");
    JodaBeanUtils.notNull(timeSeries, "timeSeries");
    this.scenarioCount = scenarioCount;
    this.valuationDate = valuationDate;
    this.values = ImmutableMap.copyOf(values);
    this.timeSeries = ImmutableMap.copyOf(timeSeries);
  }

  @Override
  public ImmutableCalculationMarketData.Meta metaBean() {
    return ImmutableCalculationMarketData.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the number of scenarios.
   * @return the value of the property
   */
  @Override
  public int getScenarioCount() {
    return scenarioCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation date associated with each scenario.
   * @return the value of the property, not null
   */
  @Override
  public MarketDataBox<LocalDate> getValuationDate() {
    return valuationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the individual items of market data.
   * @return the value of the property, not null
   */
  public ImmutableMap<MarketDataId<?>, MarketDataBox<?>> getValues() {
    return values;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-series of market data values.
   * <p>
   * If a request is made for a time-series that is not in the map, an empty series will be returned.
   * @return the value of the property, not null
   */
  public ImmutableMap<ObservableId, LocalDateDoubleTimeSeries> getTimeSeries() {
    return timeSeries;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ImmutableCalculationMarketData other = (ImmutableCalculationMarketData) obj;
      return (scenarioCount == other.scenarioCount) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(values, other.values) &&
          JodaBeanUtils.equal(timeSeries, other.timeSeries);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(scenarioCount);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(values);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeSeries);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("ImmutableCalculationMarketData{");
    buf.append("scenarioCount").append('=').append(scenarioCount).append(',').append(' ');
    buf.append("valuationDate").append('=').append(valuationDate).append(',').append(' ');
    buf.append("values").append('=').append(values).append(',').append(' ');
    buf.append("timeSeries").append('=').append(JodaBeanUtils.toString(timeSeries));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableCalculationMarketData}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code scenarioCount} property.
     */
    private final MetaProperty<Integer> scenarioCount = DirectMetaProperty.ofImmutable(
        this, "scenarioCount", ImmutableCalculationMarketData.class, Integer.TYPE);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<MarketDataBox<LocalDate>> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", ImmutableCalculationMarketData.class, (Class) MarketDataBox.class);
    /**
     * The meta-property for the {@code values} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<MarketDataId<?>, MarketDataBox<?>>> values = DirectMetaProperty.ofImmutable(
        this, "values", ImmutableCalculationMarketData.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code timeSeries} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<ObservableId, LocalDateDoubleTimeSeries>> timeSeries = DirectMetaProperty.ofImmutable(
        this, "timeSeries", ImmutableCalculationMarketData.class, (Class) ImmutableMap.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "scenarioCount",
        "valuationDate",
        "values",
        "timeSeries");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1203198113:  // scenarioCount
          return scenarioCount;
        case 113107279:  // valuationDate
          return valuationDate;
        case -823812830:  // values
          return values;
        case 779431844:  // timeSeries
          return timeSeries;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ImmutableCalculationMarketData> builder() {
      return new ImmutableCalculationMarketData.Builder();
    }

    @Override
    public Class<? extends ImmutableCalculationMarketData> beanType() {
      return ImmutableCalculationMarketData.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code scenarioCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Integer> scenarioCount() {
      return scenarioCount;
    }

    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<MarketDataBox<LocalDate>> valuationDate() {
      return valuationDate;
    }

    /**
     * The meta-property for the {@code values} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<MarketDataId<?>, MarketDataBox<?>>> values() {
      return values;
    }

    /**
     * The meta-property for the {@code timeSeries} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<ObservableId, LocalDateDoubleTimeSeries>> timeSeries() {
      return timeSeries;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1203198113:  // scenarioCount
          return ((ImmutableCalculationMarketData) bean).getScenarioCount();
        case 113107279:  // valuationDate
          return ((ImmutableCalculationMarketData) bean).getValuationDate();
        case -823812830:  // values
          return ((ImmutableCalculationMarketData) bean).getValues();
        case 779431844:  // timeSeries
          return ((ImmutableCalculationMarketData) bean).getTimeSeries();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code ImmutableCalculationMarketData}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<ImmutableCalculationMarketData> {

    private int scenarioCount;
    private MarketDataBox<LocalDate> valuationDate;
    private Map<? extends MarketDataId<?>, MarketDataBox<?>> values = ImmutableMap.of();
    private Map<? extends ObservableId, LocalDateDoubleTimeSeries> timeSeries = ImmutableMap.of();

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1203198113:  // scenarioCount
          return scenarioCount;
        case 113107279:  // valuationDate
          return valuationDate;
        case -823812830:  // values
          return values;
        case 779431844:  // timeSeries
          return timeSeries;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1203198113:  // scenarioCount
          this.scenarioCount = (Integer) newValue;
          break;
        case 113107279:  // valuationDate
          this.valuationDate = (MarketDataBox<LocalDate>) newValue;
          break;
        case -823812830:  // values
          this.values = (Map<? extends MarketDataId<?>, MarketDataBox<?>>) newValue;
          break;
        case 779431844:  // timeSeries
          this.timeSeries = (Map<? extends ObservableId, LocalDateDoubleTimeSeries>) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public ImmutableCalculationMarketData build() {
      return new ImmutableCalculationMarketData(
          scenarioCount,
          valuationDate,
          values,
          timeSeries);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("ImmutableCalculationMarketData.Builder{");
      buf.append("scenarioCount").append('=').append(JodaBeanUtils.toString(scenarioCount)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("values").append('=').append(JodaBeanUtils.toString(values)).append(',').append(' ');
      buf.append("timeSeries").append('=').append(JodaBeanUtils.toString(timeSeries));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
