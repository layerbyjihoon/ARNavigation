package com.example.arnavigation.ui.ar;

interface ArRenderTarget {
    void setDestination(String text);
    void setStatus(String text);
    void setInstruction(String text);
    void setDistance(String text);
    void setArrow(boolean visible, double angleDegrees, String description);
    void setMapActionEmphasized(boolean emphasized);
    void setEndActionVisible(boolean visible);
}
