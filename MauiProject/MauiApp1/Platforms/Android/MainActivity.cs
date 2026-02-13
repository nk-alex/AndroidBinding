using Android.App;
using Android.Content.PM;
using Android.OS;
using AndroidBinding = StripeAndroidBinding.TapToPayImplementation;

namespace MauiApp1
{
    [Activity(Theme = "@style/Maui.SplashTheme", MainLauncher = true, LaunchMode = LaunchMode.SingleTop, ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
    public class MainActivity : MauiAppCompatActivity
    {
        protected override void OnCreate(Bundle? savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            AndroidBinding.Initialize(Application);

            bool tmp = AndroidBinding.IsInitialized;

            var callback = new MyDiscoveryCallback();
            AndroidBinding.StartDiscoverReaders(this, true, callback);
        }

        public class MyDiscoveryCallback : StripeAndroidBinding.DiscoveryCallbackBase
        {
            public override void OnFailure(string? p0)
            {
                var tmp = 5;
            }

            public override void OnReadersDiscovered(string? p0)
            {
                var tmp = 5;
            }

            public override void OnSuccess()
            {
                var tmp = 5;
            }
        }
    }
}
