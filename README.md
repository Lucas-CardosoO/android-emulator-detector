# android-emulator-detector

This is a project for my graduation thesis, named: Comparative study of techniques for detecting emulators on
Android devices. The repository folders are structured as follows:

- `android-emulator-detector`: An Android App, developed to identify emulators in runtime and return the data used in the detection.
- `notebooks`: The Jupyter notebook developed in Colab used for the analysis
- `Logs`: The emulators data collected by the app and used in the analysis. Real device data is not available due to privacy concerns.

The main sources of this project are the open source repositories of [mofneko](https://github.com/mofneko/EmulatorDetector), [gingo](https://github.com/gingo/android-emulator-detector) and [framgia](https://github.com/framgia/android-emulator-detector). And the papers [_Evading android runtime analysis via sandbox detection_](https://dl.acm.org/doi/abs/10.1145/2590296.2590325) by Vidas and Christin; and [_Rethinking anti-emulation techniques for large-scale software deployment_](https://www.sciencedirect.com/science/article/pii/S0167404818310216) by Jang et al.
