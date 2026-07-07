import Foundation

extension Foundation.Bundle {
    static let module: Bundle = {
        let mainPath = Bundle.main.bundleURL.appendingPathComponent("biometric_storage_biometric_storage.bundle").path
        let buildPath = "/Users/omarhanafy/Development/MyProjects/biometric_storage/macos/biometric_storage/.build/index-build/arm64-apple-macosx/debug/biometric_storage_biometric_storage.bundle"

        let preferredBundle = Bundle(path: mainPath)

        guard let bundle = preferredBundle ?? Bundle(path: buildPath) else {
            // Users can write a function called fatalError themselves, we should be resilient against that.
            Swift.fatalError("could not load resource bundle: from \(mainPath) or \(buildPath)")
        }

        return bundle
    }()
}