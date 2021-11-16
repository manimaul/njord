# Developing on MacOS

Edit "/usr/local/Homebrew/Library/Taps/homebrew/homebrew-core/Formula/gdal.rb"
`brew edit gdal`
brew reinstall --build-from-source gdal

or

```shell 
brew tap manimaul/njord/gdal
brew install --build-from-source manimaul/njord/gdal
```