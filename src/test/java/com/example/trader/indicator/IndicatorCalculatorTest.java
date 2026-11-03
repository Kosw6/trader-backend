package com.example.trader.indicator;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class IndicatorCalculatorTest {
    @Test void insufficientPointsAreNullNotZero() {
        var points=IndicatorCalculator.calculate(new double[]{10,11,12});
        for(var p:points) { assertNull(p.rsi()); assertNull(p.macd()); assertNull(p.signal()); }
    }
    @Test void increasingDecreasingAndFlatHaveExplicitRsiBoundaries() {
        double[] up=new double[80], down=new double[80], flat=new double[80];
        for(int i=0;i<80;i++) { up[i]=100+i; down[i]=200-i; flat[i]=100; }
        assertEquals(100,IndicatorCalculator.calculate(up)[79].rsi());
        assertEquals(0,IndicatorCalculator.calculate(down)[79].rsi());
        var v=IndicatorCalculator.calculate(flat)[79];
        assertEquals(50,v.rsi()); assertEquals(0,v.macd(),1e-10);
        assertEquals(0,v.signal(),1e-10); assertEquals(0,v.histogram(),1e-10);
    }
    @Test void smaSeedsAndLinearTrendHaveKnownMacd() {
        double[] close=new double[50]; for(int i=0;i<50;i++) close[i]=i+1;
        var v=IndicatorCalculator.calculate(close);
        assertNull(v[13].rsi()); assertNotNull(v[14].rsi());
        assertNull(v[24].macd()); assertEquals(7,v[25].macd(),1e-10);
        assertNull(v[32].signal()); assertEquals(7,v[33].signal(),1e-10);
        assertEquals(0,v[49].histogram(),1e-10);
    }
    @Test void laterPricesDoNotChangeEarlierValuesAndInputIsNotMutated() {
        double[] close={44.34,44.09,44.15,43.61,44.33,44.83,45.10,45.42,45.84,
                46.08,45.89,46.03,45.61,46.28,46.28,46,46.03,46.41,46.22,45.64};
        double[] before=close.clone();
        var result=IndicatorCalculator.calculate(close);
        assertEquals(70.464135, result[14].rsi(),0.00001);
        var prefix=IndicatorCalculator.calculate(Arrays.copyOf(close,15));
        assertEquals(prefix[14],result[14]); assertArrayEquals(before,close);
    }
    @Test void invalidClosesAreRejected() {
        for(double v:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->IndicatorCalculator.calculate(new double[]{v}));
    }
}
